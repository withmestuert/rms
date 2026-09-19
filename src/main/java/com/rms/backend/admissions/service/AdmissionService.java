package com.rms.backend.admissions.service;

import com.rms.backend.admissions.dto.AdmissionRequestDto;
import com.rms.backend.admissions.dto.AdmissionResponseDto;
import com.rms.backend.admissions.dto.TenantStayCheckDto;
import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.entity.AdmissionStatus;
import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.entity.AdvancePaidStatus;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdmissionService {

    private final AdmissionRepository admissionRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;

    public AdmissionService(AdmissionRepository admissionRepository,
                            RoomRepository roomRepository,
                            TenantRepository tenantRepository) {
        this.admissionRepository = admissionRepository;
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
    }

    // -------------------------------------------------------------------------
    // ENROLLMENT (POST /api/admissions)
    // -------------------------------------------------------------------------

    @Transactional
    public AdmissionResponseDto createEnrollment(AdmissionRequestDto dto) {

        // 1. Acquire pessimistic lock on the room
        Room room = roomRepository.findByRoomNoWithLock(dto.getRoomNo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found: " + dto.getRoomNo()));

        // 2. Capacity check
        if (!room.hasAvailableCapacity()) {
            throw new DuplicateResourceException(
                    "Room " + dto.getRoomNo() + " has no available capacity");
        }

        // 3. Resolve or create tenant
        Tenant tenant = resolveOrCreateTenant(dto);

        // 4. Active admission guard — tenant cannot have two active stays
        long activeCount = admissionRepository.countByTenant_UidAndStatusIn(
                tenant.getUid(), List.of(AdmissionStatus.PENDING, AdmissionStatus.PAID));
        if (activeCount > 0) {
            throw new DuplicateResourceException(
                    "Tenant " + tenant.getName() + " is currently active in Room " + tenant.getRoomNo() + ". A resident cannot have multiple active stays simultaneously.");
        }

        // 5. Generate a unique admission number
        String admissionNumber = generateAdmissionNumber();

        // 6. Build and persist the admission
        Admission admission = new Admission();
        admission.setAdmissionNumber(admissionNumber);
        admission.setTenant(tenant);
        admission.setRoom(room);
        admission.setStatus(AdmissionStatus.PENDING);
        admission.setEnrollmentDate(
                dto.getEnrollmentDate() != null ? dto.getEnrollmentDate() : LocalDate.now());
        // Automatically inherit property ID from room
        Long propId = dto.getPropertyId();
        if (propId == null && room != null) {
            propId = room.getPropertyId();
        }
        admission.setPropertyId(propId);

        admission.setRemarks(dto.getRemarks());

        admissionRepository.save(admission);

        // 7. Increment reservedCapacity on the room
        room.setReservedCapacity(room.getReservedCapacity() + 1);
        room.recalculateAvailability();
        roomRepository.save(room);

        return toResponseDto(admission);
    }

    // -------------------------------------------------------------------------
    // CONFIRM (PUT /api/admissions/{admissionNumber}/confirm)
    // -------------------------------------------------------------------------

    @Transactional
    public AdmissionResponseDto confirmAdmission(String admissionNumber) {

        Admission admission = admissionRepository.findByAdmissionNumber(admissionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admission not found: " + admissionNumber));

        if (admission.getStatus() == AdmissionStatus.PAID) {
            throw new DuplicateResourceException("Admission is already confirmed and PAID");
        }
        if (admission.getStatus() != AdmissionStatus.PENDING) {
            throw new DuplicateResourceException(
                    "Only PENDING admissions can be confirmed. Current status: " + admission.getStatus());
        }

        // Acquire lock on the room for atomic update
        Room room = roomRepository.findByRoomNoWithLock(admission.getRoom().getRoomNo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found: " + admission.getRoom().getRoomNo()));

        // Transition: PENDING -> PAID
        admission.setStatus(AdmissionStatus.PAID);
        admission.setConfirmedOn(LocalDateTime.now());
        admissionRepository.save(admission);

        // Update tenant advance payment status
        Tenant tenant = admission.getTenant();
        tenant.setAdvancePaidStatus(AdvancePaidStatus.PAID);
        tenantRepository.save(tenant);

        // Move slot from reserved -> current occupancy
        room.setReservedCapacity(Math.max(0, room.getReservedCapacity() - 1));
        room.setCurrentOccupancy(room.getCurrentOccupancy() + 1);
        room.recalculateAvailability();
        roomRepository.save(room);

        return toResponseDto(admission);
    }

    // -------------------------------------------------------------------------
    // CANCEL (DELETE /api/admissions/{admissionNumber})
    // -------------------------------------------------------------------------

    @Transactional
    public void cancelPendingAdmission(String admissionNumber) {

        Admission admission = admissionRepository.findByAdmissionNumber(admissionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admission not found: " + admissionNumber));

        if (admission.getStatus() == AdmissionStatus.PAID) {
            throw new DuplicateResourceException(
                    "Cannot cancel a PAID admission through the pending cancellation workflow");
        }
        if (admission.getStatus() != AdmissionStatus.PENDING) {
            throw new DuplicateResourceException(
                    "Only PENDING admissions can be cancelled. Current status: " + admission.getStatus());
        }

        // Acquire lock on the room
        Room room = roomRepository.findByRoomNoWithLock(admission.getRoom().getRoomNo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found: " + admission.getRoom().getRoomNo()));

        Tenant tenant = admission.getTenant();

        // Release reservation slot
        room.setReservedCapacity(Math.max(0, room.getReservedCapacity() - 1));
        room.recalculateAvailability();
        roomRepository.save(room);

        // Delete the admission
        admissionRepository.delete(admission);

        // Delete tenant only if they have NO other admission history
        long remainingAdmissions = admissionRepository.countByTenant_UidAndAdmissionNumberNot(
                tenant.getUid(), admissionNumber);
        if (remainingAdmissions == 0) {
            tenantRepository.delete(tenant);
        }
    }

    // -------------------------------------------------------------------------
    // QUERY METHODS
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdmissionResponseDto> getAllAdmissions() {
        return admissionRepository.findAll()
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AdmissionResponseDto> getAdmissionsByPropertyId(Long propertyId) {
        if (propertyId == null) {
            return getAllAdmissions();
        }
        return admissionRepository.findByPropertyId(propertyId)
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdmissionResponseDto getAdmissionByNumber(String admissionNumber) {
        Admission admission = admissionRepository.findByAdmissionNumber(admissionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admission not found: " + admissionNumber));
        return toResponseDto(admission);
    }

    @Transactional(readOnly = true)
    public TenantStayCheckDto checkExistingTenant(String aadhaarNo, String mobileNumber) {
        Tenant tenant = null;
        if (aadhaarNo != null && !aadhaarNo.isBlank()) {
            tenant = tenantRepository.findByAadhaarNo(aadhaarNo.trim()).orElse(null);
        }
        if (tenant == null && mobileNumber != null && !mobileNumber.isBlank()) {
            tenant = tenantRepository.findByMobileNumber(mobileNumber.trim()).orElse(null);
        }

        if (tenant == null) {
            TenantStayCheckDto dto = new TenantStayCheckDto();
            dto.setExists(false);
            return dto;
        }

        TenantStayCheckDto dto = new TenantStayCheckDto();
        dto.setExists(true);
        dto.setTenantUid(tenant.getUid());
        dto.setTenantName(tenant.getName());
        dto.setAadhaarNo(tenant.getAadhaarNo());
        dto.setMobileNumber(tenant.getMobileNumber());
        dto.setTenantType(tenant.getTenantType());
        dto.setOrganizationName(tenant.getOrganizationName());
        dto.setParentContact(tenant.getParentContact());
        dto.setStandardRent(tenant.getStandardRent());
        dto.setAdvancePaid(tenant.getAdvancePaid());

        boolean isCurrentlyActive = "ACTIVE".equalsIgnoreCase(tenant.getStatus()) || tenant.getStatus() == null;
        List<Admission> allAdmissions = admissionRepository.findByTenant_UidOrderByEnrollmentDateDesc(tenant.getUid());
        dto.setTotalPreviousStays(allAdmissions.size());

        boolean hasActiveStay = isCurrentlyActive && allAdmissions.stream()
                .anyMatch(a -> a.getStatus() == AdmissionStatus.PENDING || a.getStatus() == AdmissionStatus.PAID);
        dto.setHasActiveStay(hasActiveStay);
        if (hasActiveStay) {
            dto.setActiveRoomNo(tenant.getRoomNo());
        }

        if (!allAdmissions.isEmpty()) {
            Admission lastStay = allAdmissions.get(0);
            dto.setLastStayFrom(lastStay.getEnrollmentDate());
            LocalDate toDate = lastStay.getVacatedOn();
            if (toDate == null) {
                toDate = lastStay.getUpdatedAt() != null ? lastStay.getUpdatedAt().toLocalDate() : lastStay.getEnrollmentDate();
            }
            dto.setLastStayTo(toDate);
        }

        return dto;
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    /**
     * Resolves an existing Tenant from UID, Aadhaar, or mobile number.
     * If none match, creates a new Tenant from the DTO fields.
     */
    private Tenant resolveOrCreateTenant(AdmissionRequestDto dto) {

        Tenant tenant = null;

        // Try to find by UID first
        if (dto.getTenantUid() != null && !dto.getTenantUid().isBlank()) {
            tenant = tenantRepository.findById(dto.getTenantUid())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant not found with UID: " + dto.getTenantUid()));
        }
        if (tenant == null && dto.getAadhaarNo() != null && !dto.getAadhaarNo().isBlank()) {
            tenant = tenantRepository.findByAadhaarNo(dto.getAadhaarNo().trim()).orElse(null);
        }
        if (tenant == null && dto.getMobileNumber() != null && !dto.getMobileNumber().isBlank()) {
            tenant = tenantRepository.findByMobileNumber(dto.getMobileNumber().trim()).orElse(null);
        }

        if (tenant != null) {
            // If returning resident (previously inactive/vacated), reactivate for new stay
            if ("INACTIVE".equalsIgnoreCase(tenant.getStatus())) {
                // Ensure any prior open admissions are marked VACATED
                List<Admission> pastActive = admissionRepository.findByTenant_Uid(tenant.getUid()).stream()
                        .filter(a -> a.getStatus() == AdmissionStatus.PENDING || a.getStatus() == AdmissionStatus.PAID)
                        .toList();
                for (Admission a : pastActive) {
                    a.setStatus(AdmissionStatus.VACATED);
                    if (a.getVacatedOn() == null) {
                        a.setVacatedOn(a.getUpdatedAt() != null ? a.getUpdatedAt().toLocalDate() : LocalDate.now());
                    }
                    admissionRepository.save(a);
                }

                // Reactivate tenant and update to new stay details
                tenant.setStatus("ACTIVE");
                tenant.setRoomNo(dto.getRoomNo());
                if (dto.getName() != null && !dto.getName().isBlank()) {
                    tenant.setName(dto.getName());
                }
                if (dto.getTenantType() != null && !dto.getTenantType().isBlank()) {
                    tenant.setTenantType(dto.getTenantType());
                }
                if (dto.getOrganizationName() != null && !dto.getOrganizationName().isBlank()) {
                    tenant.setOrganizationName(dto.getOrganizationName());
                }
                if (dto.getParentContact() != null) {
                    tenant.setParentContact(dto.getParentContact());
                }
                if (dto.getStandardRent() != null) {
                    tenant.setStandardRent(dto.getStandardRent());
                }
                if (dto.getAdvancePaid() != null) {
                    tenant.setAdvancePaid(dto.getAdvancePaid());
                }
                tenant.setAdvancePaidStatus(AdvancePaidStatus.PENDING);
                Long propId = dto.getPropertyId();
                if (propId != null) {
                    tenant.setPropertyId(propId);
                }
                return tenantRepository.save(tenant);
            }
            return tenant;
        }

        // New tenant — validate mandatory fields
        validateNewTenantFields(dto);

        tenant = new Tenant();
        tenant.setUid(UUID.randomUUID().toString());
        tenant.setName(dto.getName());
        tenant.setAadhaarNo(dto.getAadhaarNo());
        tenant.setMobileNumber(dto.getMobileNumber());
        tenant.setTenantType(dto.getTenantType());
        tenant.setOrganizationName(dto.getOrganizationName());
        tenant.setParentContact(dto.getParentContact());
        tenant.setRoomNo(dto.getRoomNo());
        tenant.setAdvancePaid(dto.getAdvancePaid() != null ? dto.getAdvancePaid() : 0);
        tenant.setAdvancePaidStatus(AdvancePaidStatus.PENDING);
        tenant.setStandardRent(dto.getStandardRent() != null ? dto.getStandardRent() : 0);

        // Inherit propertyId from room
        Long propId = dto.getPropertyId();
        if (propId == null) {
            Room r = roomRepository.findById(dto.getRoomNo()).orElse(null);
            if (r != null) {
                propId = r.getPropertyId();
            }
        }
        tenant.setPropertyId(propId);

        return tenantRepository.save(tenant);
    }

    private void validateNewTenantFields(AdmissionRequestDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Tenant name is required for new enrollment");
        }
        if (dto.getAadhaarNo() == null || dto.getAadhaarNo().isBlank()) {
            throw new IllegalArgumentException("Aadhaar number is required for new enrollment");
        }
        if (dto.getMobileNumber() == null || dto.getMobileNumber().isBlank()) {
            throw new IllegalArgumentException("Mobile number is required for new enrollment");
        }
        if (dto.getTenantType() == null || dto.getTenantType().isBlank()) {
            throw new IllegalArgumentException("Tenant type is required for new enrollment");
        }
        if (dto.getOrganizationName() == null || dto.getOrganizationName().isBlank()) {
            throw new IllegalArgumentException("Organization name is required for new enrollment");
        }
    }

    /**
     * Generates a unique admission number of the form ADM-XXXX.
     * Uses last admission ID + 1 as the sequence. Falls back to timestamp if none.
     */
    private String generateAdmissionNumber() {
        Optional<Admission> last = admissionRepository.findTopByOrderByIdDesc();
        long nextSeq = last.map(a -> a.getId() + 1).orElse(1001L);
        return String.format("ADM-%04d", nextSeq);
    }

    /**
     * Maps an Admission entity to the response DTO.
     */
    private AdmissionResponseDto toResponseDto(Admission admission) {
        AdmissionResponseDto dto = new AdmissionResponseDto();
        dto.setAdmissionNumber(admission.getAdmissionNumber());

        if (admission.getRoom() != null) {
            dto.setRoomNo(admission.getRoom().getRoomNo());
            dto.setRoomRent(admission.getRoom().getRentPerMonth());
        }

        if (admission.getTenant() != null) {
            Tenant t = admission.getTenant();
            dto.setTenantUid(t.getUid());
            dto.setTenantName(t.getName());
            dto.setAadhaarNo(t.getAadhaarNo());
            dto.setMobileNumber(t.getMobileNumber());
            dto.setAdvancePaid(t.getAdvancePaid());
            dto.setAdvancePaidStatus(t.getAdvancePaidStatus());
            dto.setTenantStatus(t.getStatus());
            dto.setParentContact(t.getParentContact());
            if (t.getStandardRent() != null) {
                dto.setRoomRent(t.getStandardRent());
            }
        }

        dto.setStatus(admission.getStatus());
        dto.setEnrollmentDate(admission.getEnrollmentDate());
        dto.setRemarks(admission.getRemarks());
        dto.setConfirmedOn(admission.getConfirmedOn());
        dto.setVacatedOn(admission.getVacatedOn());
        dto.setCreatedAt(admission.getCreatedAt());
        dto.setUpdatedAt(admission.getUpdatedAt());
        dto.setPropertyId(admission.getPropertyId());

        return dto;
    }
}
