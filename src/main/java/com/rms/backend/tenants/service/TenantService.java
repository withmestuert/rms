package com.rms.backend.tenants.service;

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

import java.util.List;
//tested

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final RoomRepository roomRepository;

    public TenantService(TenantRepository tenantRepository, RoomRepository roomRepository) {
        this.tenantRepository = tenantRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public Tenant createTenant(TenantRequestDto dto) {
        // 1. Duplicate check: UID (Primary Key)
        if (tenantRepository.existsById(dto.getUid())) {
            throw new DuplicateResourceException(
                    "Tenant already exists with UID: " + dto.getUid()
            );
        }

        // 2. Duplicate check: Aadhaar number (Candidate Key)
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
        long currentCount = tenantRepository.countByRoomNo(dto.getRoomNo());
        if (currentCount >= room.getOccupancy()) {
            throw new DuplicateResourceException(
                    "Room " + dto.getRoomNo() + " is already at maximum capacity (" +
                            currentCount + "/" + room.getOccupancy() + ")"
            );
        }

        // 6. Map and persist tenant
        Tenant tenant = new Tenant();
        tenant.setUid(dto.getUid());
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

        Tenant savedTenant = tenantRepository.save(tenant);

        // 7. Update room availability status if now completely filled
        if (currentCount + 1 >= room.getOccupancy()) {
            room.setAvailable(false);
            roomRepository.save(room);
        }

        return savedTenant;
    }

    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    public Tenant getTenantByUid(String uid) {
        return tenantRepository.findById(uid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant not found with UID: " + uid)
                );
    }

    public List<Tenant> getTenantsByRoomNo(String roomNo) {
        return tenantRepository.findByRoomNo(roomNo);
    }

    @Transactional
    public Tenant updateTenant(String uid, TenantRequestDto dto) {
        Tenant existingTenant = tenantRepository.findById(uid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant not found with UID: " + uid)
                );

        // Check if aadhaar changed and already in use by another tenant
        if (!existingTenant.getAadhaarNo().equals(dto.getAadhaarNo()) &&
                tenantRepository.existsByAadhaarNo(dto.getAadhaarNo())) {
            throw new DuplicateResourceException(
                    "Aadhaar number already in use: " + dto.getAadhaarNo()
            );
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

            long targetCount = tenantRepository.countByRoomNo(newRoomNo);
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
        existingTenant.setAadhaarNo(dto.getAadhaarNo());
        existingTenant.setMobileNumber(dto.getMobileNumber());
        existingTenant.setTenantType(dto.getTenantType());
        existingTenant.setOrganizationName(dto.getOrganizationName());
        existingTenant.setParentContact(dto.getParentContact());
        existingTenant.setAdvancePaid(dto.getAdvancePaid());
        existingTenant.setStandardRent(dto.getStandardRent());

        return tenantRepository.save(existingTenant);
    }

    @Transactional
    public void deleteTenant(String uid) {
        Tenant existingTenant = tenantRepository.findById(uid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Tenant not found with UID: " + uid)
                );

        String roomNo = existingTenant.getRoomNo();
        tenantRepository.delete(existingTenant);

        // Since a tenant is deleted, their room now has an available bed slot
        roomRepository.findById(roomNo).ifPresent(room -> {
            room.setAvailable(true);
            roomRepository.save(room);
        });
    }
}
