package com.rms.backend.admissions.service;

import com.rms.backend.admissions.dto.AdmissionRequestDto;
import com.rms.backend.admissions.dto.AdmissionResponseDto;
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
                    "Tenant " + tenant.getUid() + " already has an active admission");
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
    public AdmissionResponseDto getAdmissionByNumber(String admissionNumber) {
        Admission admission = admissionRepository.findByAdmissionNumber(admissionNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admission not found: " + admissionNumber));
        return toResponseDto(admission);
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    /**
     * Resolves an existing Tenant from UID, Aadhaar, or mobile number.
     * If none match, creates a new Tenant from the DTO fields.
     */
    private Tenant resolveOrCreateTenant(AdmissionRequestDto dto) {

        // Try to find by UID first
        if (dto.getTenantUid() != null && !dto.getTenantUid().isBlank()) {
            return tenantRepository.findById(dto.getTenantUid())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tenant not found with UID: " + dto.getTenantUid()));
        }

        // Try Aadhaar
        if (dto.getAadhaarNo() != null && !dto.getAadhaarNo().isBlank()) {
            Optional<Tenant> byAadhaar = tenantRepository.findByAadhaarNo(dto.getAadhaarNo());
            if (byAadhaar.isPresent()) {
                return byAadhaar.get();
            }
        }

        // Try mobile
        if (dto.getMobileNumber() != null && !dto.getMobileNumber().isBlank()) {
            Optional<Tenant> byMobile = tenantRepository.findByMobileNumber(dto.getMobileNumber());
            if (byMobile.isPresent()) {
                return byMobile.get();
            }
        }

        // New tenant — validate mandatory fields
        validateNewTenantFields(dto);

        Tenant tenant = new Tenant();
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

        if (admission.getTenant() != null) {
            Tenant t = admission.getTenant();
            dto.setTenantUid(t.getUid());
            dto.setTenantName(t.getName());
            dto.setAadhaarNo(t.getAadhaarNo());
            dto.setMobileNumber(t.getMobileNumber());
            dto.setAdvancePaid(t.getAdvancePaid());
            dto.setAdvancePaidStatus(t.getAdvancePaidStatus());
        }

        if (admission.getRoom() != null) {
            dto.setRoomNo(admission.getRoom().getRoomNo());
            dto.setRoomRent(admission.getRoom().getRentPerMonth());
        }

        dto.setStatus(admission.getStatus());
        dto.setEnrollmentDate(admission.getEnrollmentDate());
        dto.setRemarks(admission.getRemarks());
        dto.setConfirmedOn(admission.getConfirmedOn());
        dto.setCreatedAt(admission.getCreatedAt());
        dto.setUpdatedAt(admission.getUpdatedAt());

        return dto;
    }
}
