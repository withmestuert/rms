package com.rms.backend.tenants.service;

import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.entity.AdmissionStatus;
import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.dto.TenantRequestDto;
import com.rms.backend.tenants.entity.AdvancePaidStatus;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
//tested

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final RoomRepository roomRepository;
    private final AdmissionRepository admissionRepository;

    public TenantService(TenantRepository tenantRepository,
                         RoomRepository roomRepository,
                         AdmissionRepository admissionRepository) {
        this.tenantRepository = tenantRepository;
        this.roomRepository = roomRepository;
        this.admissionRepository = admissionRepository;
    }

    @Transactional
    public Tenant createTenant(TenantRequestDto dto) {
        // 1. Duplicate check: UID (Primary Key)
        String effectiveUid = (dto.getUid() != null && !dto.getUid().isBlank())
                ? dto.getUid().trim()
                : java.util.UUID.randomUUID().toString();
        if (tenantRepository.existsById(effectiveUid)) {
            throw new DuplicateResourceException(
                    "Tenant already exists with UID: " + effectiveUid
            );
        }

        // 2. Duplicate check: Aadhaar number (Candidate Key)
        if (dto.getAadhaarNo() == null || dto.getAadhaarNo().isBlank()) {
            throw new IllegalArgumentException("Aadhaar number is required");
        }
        if (tenantRepository.existsByAadhaarNo(dto.getAadhaarNo())) {
            throw new DuplicateResourceException(
                    "Tenant already exists with Aadhaar number: " + dto.getAadhaarNo()
            );
        }

        // 3. Duplicate check: Mobile number
        if (tenantRepository.existsByMobileNumber(dto.getMobileNumber())) {
            throw new DuplicateResourceException(
                    "Tenant already exists with mobile number: " + dto.getMobileNumber()
            );
        }

        // 4. Verify room exists
        Room room = roomRepository.findById(dto.getRoomNo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Room not found with room number: " + dto.getRoomNo()
                ));

        // 5. Verify room occupancy capacity
        long currentCount = tenantRepository.countActiveByRoomNo(dto.getRoomNo());
        if (currentCount >= room.getOccupancy()) {
            throw new DuplicateResourceException(
                    "Room " + dto.getRoomNo() + " is already at maximum capacity (" +
                            currentCount + "/" + room.getOccupancy() + ")"
            );
        }

        // 6. Map and persist tenant
        Tenant tenant = new Tenant();
        tenant.setUid(effectiveUid);
        tenant.setName(dto.getName());
        tenant.setAadhaarNo(dto.getAadhaarNo());
        tenant.setMobileNumber(dto.getMobileNumber());
        tenant.setTenantType(dto.getTenantType());
        tenant.setOrganizationName(dto.getOrganizationName());
        tenant.setParentContact(dto.getParentContact());
        tenant.setRoomNo(dto.getRoomNo());
        tenant.setAdvancePaid(dto.getAdvancePaid());
        tenant.setAdvancePaidStatus(AdvancePaidStatus.PENDING);
        tenant.setStandardRent(dto.getStandardRent());

        // Automatically assign property ID from room (or dto if provided)
        Long propId = dto.getPropertyId();
        if (propId == null && room != null) {
            propId = room.getPropertyId();
        }
        tenant.setPropertyId(propId);

        Tenant savedTenant = tenantRepository.save(tenant);

        // 7. Update room availability status if now completely filled
        if (currentCount + 1 >= room.getOccupancy()) {
            room.setAvailable(false);
            roomRepository.save(room);
        }

        return savedTenant;
    }

    public List<Tenant> getAllTenants() {
        return tenantRepository.findAllActive();
    }

    public List<Tenant> getTenantsByPropertyId(Long propertyId) {
        if (propertyId == null) {
            return getAllTenants();
        }
        return tenantRepository.findActiveByPropertyId(propertyId);
    }

    public Tenant getTenantByUid(String uid) {
        return tenantRepository.findById(uid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant not found with UID: " + uid)
                );
    }

    public List<Tenant> getTenantsByRoomNo(String roomNo) {
        return tenantRepository.findActiveByRoomNo(roomNo);
    }

    @Transactional
    public Tenant updateTenant(String uid, TenantRequestDto dto) {
        Tenant existingTenant = tenantRepository.findById(uid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant not found with UID: " + uid)
                );

        // Check if aadhaar changed and already in use by another tenant
        if (dto.getAadhaarNo() != null && !dto.getAadhaarNo().isBlank()) {
            if (!dto.getAadhaarNo().equals(existingTenant.getAadhaarNo()) &&
                    tenantRepository.existsByAadhaarNo(dto.getAadhaarNo())) {
                throw new DuplicateResourceException(
                        "Aadhaar number already in use: " + dto.getAadhaarNo()
                );
            }
            existingTenant.setAadhaarNo(dto.getAadhaarNo());
        }

        // Check if mobile changed and already in use by another tenant
        if (!existingTenant.getMobileNumber().equals(dto.getMobileNumber()) &&
                tenantRepository.existsByMobileNumber(dto.getMobileNumber())) {
            throw new DuplicateResourceException(
                    "Mobile number already in use: " + dto.getMobileNumber()
            );
        }

        // Handle room change if applicable
        String oldRoomNo = existingTenant.getRoomNo();
        String newRoomNo = dto.getRoomNo();
        if (!newRoomNo.equalsIgnoreCase(oldRoomNo)) {
            Room targetRoom = roomRepository.findById(newRoomNo)
                    .orElseThrow(() -> new ResourceNotFoundException("Target room not found: " + newRoomNo));

            long targetCount = tenantRepository.countActiveByRoomNo(newRoomNo);
            if (targetCount >= targetRoom.getOccupancy()) {
                throw new DuplicateResourceException("Target room " + newRoomNo + " is full");
            }

            existingTenant.setRoomNo(newRoomNo);

            if (targetCount + 1 >= targetRoom.getOccupancy()) {
                targetRoom.setAvailable(false);
                roomRepository.save(targetRoom);
            }

            // Free up old room availability
            roomRepository.findById(oldRoomNo).ifPresent(oldRoom -> {
                oldRoom.setAvailable(true);
                roomRepository.save(oldRoom);
            });
        }

        existingTenant.setName(dto.getName());
        existingTenant.setMobileNumber(dto.getMobileNumber());
        existingTenant.setTenantType(dto.getTenantType());
        existingTenant.setOrganizationName(dto.getOrganizationName());
        existingTenant.setParentContact(dto.getParentContact());
        existingTenant.setAdvancePaid(dto.getAdvancePaid());
        existingTenant.setStandardRent(dto.getStandardRent());
        if (dto.getPropertyId() != null) {
            existingTenant.setPropertyId(dto.getPropertyId());
        }

        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            existingTenant.setStatus(dto.getStatus().trim().toUpperCase());
        } else if ("INACTIVE".equalsIgnoreCase(existingTenant.getStatus())) {
            existingTenant.setStatus("ACTIVE");
        }

        return tenantRepository.save(existingTenant);
    }

    @Transactional
    public void deleteTenant(String uid) {
        Tenant existingTenant = tenantRepository.findById(uid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant not found with UID: " + uid)
                );

        existingTenant.setStatus("INACTIVE");
        tenantRepository.save(existingTenant);

        // Mark any active admissions for this tenant as VACATED
        List<Admission> activeAdmissions = admissionRepository.findByTenant_Uid(uid).stream()
                .filter(adm -> adm.getStatus() == AdmissionStatus.PENDING || adm.getStatus() == AdmissionStatus.PAID)
                .toList();
        for (Admission adm : activeAdmissions) {
            adm.setStatus(AdmissionStatus.VACATED);
            adm.setVacatedOn(LocalDate.now());
            admissionRepository.save(adm);
        }

        String roomNo = existingTenant.getRoomNo();
        if (roomNo != null) {
            roomRepository.findById(roomNo).ifPresent(room -> {
                long activeCount = tenantRepository.countActiveByRoomNo(roomNo);
                room.setCurrentOccupancy((int) activeCount);
                room.recalculateAvailability();
                roomRepository.save(room);
            });
        }
    }

    @Transactional
    public Tenant verifyAdvancePayment(String uid, Integer amount) {
        Tenant tenant = tenantRepository.findById(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with UID: " + uid));

        tenant.setAdvancePaidStatus(AdvancePaidStatus.PAID);
        if (amount != null && amount > 0) {
            tenant.setAdvancePaid(amount);
        }

        // If there is an associated pending admission, confirm it and adjust room capacities
        List<Admission> pendingAdmissions = admissionRepository.findByTenant_Uid(uid).stream()
                .filter(adm -> adm.getStatus() == AdmissionStatus.PENDING)
                .toList();

        for (Admission admission : pendingAdmissions) {
            admission.setStatus(AdmissionStatus.PAID);
            admission.setConfirmedOn(LocalDateTime.now());
            admissionRepository.save(admission);

            Room room = admission.getRoom();
            if (room != null) {
                room.setReservedCapacity(Math.max(0, room.getReservedCapacity() - 1));
                room.setCurrentOccupancy(room.getCurrentOccupancy() + 1);
                room.recalculateAvailability();
                roomRepository.save(room);
            }
        }

        return tenantRepository.save(tenant);
    }
}
