package com.rms.backend.rooms.service;

import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.entity.AdmissionStatus;
import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.properties.entity.Block;
import com.rms.backend.properties.entity.Floor;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.BlockRepository;
import com.rms.backend.properties.repository.FloorRepository;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.rooms.entity.Bed;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.BedRepository;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rms.backend.rooms.dto.RoomRequestDto;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.exception.DuplicateResourceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final AdmissionRepository admissionRepository;
    private final PropertyRepository propertyRepository;
    private final BlockRepository blockRepository;
    private final FloorRepository floorRepository;
    private final BedRepository bedRepository;

    public RoomService(RoomRepository roomRepository,
                       TenantRepository tenantRepository,
                       AdmissionRepository admissionRepository,
                       PropertyRepository propertyRepository,
                       BlockRepository blockRepository,
                       FloorRepository floorRepository,
                       BedRepository bedRepository) {
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.admissionRepository = admissionRepository;
        this.propertyRepository = propertyRepository;
        this.blockRepository = blockRepository;
        this.floorRepository = floorRepository;
        this.bedRepository = bedRepository;
    }

    public Room createRoom(Room room) {
        return roomRepository.save(room);
    }

    @Transactional
    public Room syncRoomOccupancy(Room room) {
        if (room == null) return null;
        long activeCount = tenantRepository.countActiveByRoomNo(room.getRoomNo());
        long pendingReservations = admissionRepository.findByRoom_RoomNo(room.getRoomNo()).stream()
                .filter(a -> a.getStatus() == AdmissionStatus.PENDING)
                .count();

        boolean changed = false;
        if (room.getCurrentOccupancy() == null || room.getCurrentOccupancy() != (int) activeCount) {
            room.setCurrentOccupancy((int) activeCount);
            changed = true;
        }
        if (room.getReservedCapacity() == null || room.getReservedCapacity() != (int) pendingReservations) {
            room.setReservedCapacity((int) pendingReservations);
            changed = true;
        }

        int capacity = room.getOccupancy() != null ? room.getOccupancy() : 0;
        int maintenanceCount = 0;

        // Reconcile bed status if beds exist
        if (room.getId() != null) {
            List<Bed> beds = bedRepository.findByRoomIdOrderByBedNumberAsc(room.getId());
            if (!beds.isEmpty()) {
                // Find active tenants for this room to check explicit bed assignments
                List<Tenant> roomTenants = tenantRepository.findByRoomNo(room.getRoomNo()).stream()
                        .filter(t -> t.getStatus() == null || !"INACTIVE".equalsIgnoreCase(t.getStatus()))
                        .toList();

                java.util.Set<Long> occupiedBedIds = new java.util.HashSet<>();
                int unassignedTenants = 0;
                for (Tenant t : roomTenants) {
                    if (t.getBedId() != null) {
                        occupiedBedIds.add(t.getBedId());
                    } else {
                        unassignedTenants++;
                    }
                }

                // Check pending admissions for explicit bed assignments
                List<Admission> pendingAdmissions = admissionRepository.findByRoom_RoomNo(room.getRoomNo()).stream()
                        .filter(a -> a.getStatus() == AdmissionStatus.PENDING)
                        .toList();
                java.util.Set<Long> reservedBedIds = new java.util.HashSet<>();
                int unassignedReservations = 0;
                for (Admission a : pendingAdmissions) {
                    if (a.getBedId() != null) {
                        reservedBedIds.add(a.getBedId());
                    } else {
                        unassignedReservations++;
                    }
                }

                for (Bed bed : beds) {
                    String currentStatus = bed.getStatus() != null ? bed.getStatus().toUpperCase() : "AVAILABLE";
                    if ("MAINTENANCE".equalsIgnoreCase(currentStatus)) {
                        maintenanceCount++;
                        continue; // Never overwrite MAINTENANCE!
                    }

                    String expectedStatus;
                    if (occupiedBedIds.contains(bed.getId())) {
                        expectedStatus = "OCCUPIED";
                    } else if (reservedBedIds.contains(bed.getId())) {
                        expectedStatus = "RESERVED";
                    } else if (unassignedTenants > 0) {
                        expectedStatus = "OCCUPIED";
                        unassignedTenants--;
                    } else if (unassignedReservations > 0) {
                        expectedStatus = "RESERVED";
                        unassignedReservations--;
                    } else {
                        expectedStatus = "AVAILABLE";
                    }

                    if (!expectedStatus.equalsIgnoreCase(currentStatus)) {
                        bed.setStatus(expectedStatus);
                        bedRepository.save(bed);
                    }
                }
            }
        }

        int effectiveAvailable = Math.max(0, capacity - (int) activeCount - (int) pendingReservations - maintenanceCount);
        boolean shouldBeAvailable = effectiveAvailable > 0;
        if (room.getAvailable() == null || room.getAvailable() != shouldBeAvailable) {
            room.setAvailable(shouldBeAvailable);
            changed = true;
        }

        if (changed) {
            return roomRepository.save(room);
        }
        return room;
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(this::syncRoomOccupancy)
                .toList();
    }

    public List<Room> getRoomsByPropertyId(Long propertyId) {
        List<Room> rooms = propertyId == null
                ? roomRepository.findAll()
                : roomRepository.findByPropertyId(propertyId);
        return rooms.stream()
                .map(this::syncRoomOccupancy)
                .toList();
    }

    public Room getRoomByRoomNo(String roomNo) {
        Room room = roomRepository.findById(roomNo)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found with room number: " + roomNo)
                );
        return syncRoomOccupancy(room);
    }

    public List<Bed> getBedsForRoom(String roomNo) {
        Room room = getRoomByRoomNo(roomNo);
        Long roomId = room.getId();
        if (roomId == null) {
            roomId = roomRepository.findSurrogateIdByRoomNo(roomNo).orElse(null);
        }
        if (roomId == null) {
            return List.of();
        }
        return bedRepository.findByRoomIdOrderByBedNumberAsc(roomId);
    }

    @Transactional
    public Room updateRoom(String roomNo, RoomRequestDto roomRequestDTO) {
        Room existingRoom = roomRepository.findById(roomNo)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found with room number: " + roomNo)
                );

        if (roomRequestDTO.getFloorId() != null) {
            existingRoom.setFloorId(roomRequestDTO.getFloorId());
            floorRepository.findById(roomRequestDTO.getFloorId()).ifPresent(f -> {
                existingRoom.setFloor(String.valueOf(f.getFloorNumber()));
            });
        } else if (roomRequestDTO.getFloor() != null && !roomRequestDTO.getFloor().equals(existingRoom.getFloor())) {
            existingRoom.setFloor(roomRequestDTO.getFloor());
            Long floorId = resolveOrCreateFloorId(existingRoom.getPropertyId(), roomRequestDTO.getFloor(), roomRequestDTO.getBlockId());
            existingRoom.setFloorId(floorId);
        }

        existingRoom.setRoomType(roomRequestDTO.getRoomType());
        existingRoom.setRentPerMonth(roomRequestDTO.getRentPerMonth());

        int activeOccupancy = (existingRoom.getCurrentOccupancy() != null ? existingRoom.getCurrentOccupancy() : 0)
                + (existingRoom.getReservedCapacity() != null ? existingRoom.getReservedCapacity() : 0);
        if (roomRequestDTO.getOccupancy() != null && roomRequestDTO.getOccupancy() < activeOccupancy) {
            throw new IllegalArgumentException(
                    "Cannot reduce room occupancy to " + roomRequestDTO.getOccupancy() +
                    " because " + activeOccupancy + " occupant(s)/reservation(s) are active."
            );
        }

        existingRoom.setOccupancy(roomRequestDTO.getOccupancy());
        existingRoom.setAvailable(roomRequestDTO.getAvailable());

        Room updated = roomRepository.save(existingRoom);
        syncBedsForRoom(updated, roomRequestDTO.getOccupancy(), roomRequestDTO.getRentPerMonth());

        return syncRoomOccupancy(updated);
    }

    @Transactional
    public Room createRoom(RoomRequestDto roomRequestDTO) {
        Long propId = roomRequestDTO.getPropertyId();
        if (propId == null) {
            List<Property> activeProps = propertyRepository.findByStatus("ACTIVE");
            if (!activeProps.isEmpty()) {
                propId = activeProps.get(0).getId();
            } else {
                List<Property> allProps = propertyRepository.findAll();
                if (!allProps.isEmpty()) {
                    propId = allProps.get(0).getId();
                }
            }
        }

        if (propId != null && roomRepository.findByPropertyIdAndRoomNo(propId, roomRequestDTO.getRoomNo()).isPresent()) {
            throw new DuplicateResourceException(
                    "Room already exists with room number: " + roomRequestDTO.getRoomNo() + " in this property"
            );
        }

        if (roomRepository.existsById(roomRequestDTO.getRoomNo())) {
            throw new DuplicateResourceException(
                    "Room already exists with room number: " + roomRequestDTO.getRoomNo()
            );
        }

        Long floorId = roomRequestDTO.getFloorId();
        if (floorId != null) {
            Floor f = floorRepository.findById(floorId).orElse(null);
            if (f != null && (roomRequestDTO.getFloor() == null || roomRequestDTO.getFloor().isBlank())) {
                roomRequestDTO.setFloor(String.valueOf(f.getFloorNumber()));
            }
        }
        if (floorId == null) {
            floorId = resolveOrCreateFloorId(propId, roomRequestDTO.getFloor(), roomRequestDTO.getBlockId());
        }

        Room room = new Room();
        room.setRoomNo(roomRequestDTO.getRoomNo());
        room.setFloor(roomRequestDTO.getFloor());
        room.setFloorId(floorId);
        room.setRoomType(roomRequestDTO.getRoomType());
        room.setRentPerMonth(roomRequestDTO.getRentPerMonth());
        room.setOccupancy(roomRequestDTO.getOccupancy());
        room.setPropertyId(propId);
        room.setCurrentOccupancy(0);
        room.setReservedCapacity(0);
        room.setAvailable(roomRequestDTO.getAvailable() != null ? roomRequestDTO.getAvailable() : true);

        Room savedRoom = roomRepository.save(room);

        Long surrogateId = roomRepository.findSurrogateIdByRoomNo(savedRoom.getRoomNo()).orElse(null);
        savedRoom.setId(surrogateId);
        syncBedsForRoom(savedRoom, roomRequestDTO.getOccupancy(), roomRequestDTO.getRentPerMonth());

        return savedRoom;
    }

    @Transactional
    public void deleteRoom(String roomNo) {
        Room existingRoom = roomRepository.findById(roomNo)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Room not found with room number: " + roomNo
                        )
                );

        if (existingRoom.getCurrentOccupancy() != null && existingRoom.getCurrentOccupancy() > 0) {
            throw new DuplicateResourceException(
                    "Cannot delete room " + roomNo + " because it is currently occupied (" + existingRoom.getCurrentOccupancy() + " occupant(s))"
            );
        }

        if (existingRoom.getReservedCapacity() != null && existingRoom.getReservedCapacity() > 0) {
            throw new DuplicateResourceException(
                    "Cannot delete room " + roomNo + " because it has active reservations (" + existingRoom.getReservedCapacity() + " reserved slot(s))"
            );
        }

        long tenantCount = tenantRepository.countByRoomNo(roomNo);
        if (tenantCount > 0) {
            throw new DuplicateResourceException(
                    "Cannot delete room " + roomNo + " because " + tenantCount + " tenant(s) are assigned to it"
            );
        }

        List<Admission> admissions = admissionRepository.findByRoom_RoomNo(roomNo);
        if (!admissions.isEmpty()) {
            throw new DuplicateResourceException(
                    "Cannot delete room " + roomNo + " because " + admissions.size() + " admission record(s) reference it"
            );
        }

        if (existingRoom.getId() != null) {
            bedRepository.deleteByRoomId(existingRoom.getId());
        }

        roomRepository.delete(existingRoom);
    }

    @Transactional
    public void syncBedsForRoom(Room room, int targetOccupancy, Integer rent) {
        if (room == null) {
            return;
        }

        Long roomId = room.getId();
        if (roomId == null && room.getRoomNo() != null) {
            roomId = roomRepository.findSurrogateIdByRoomNo(room.getRoomNo()).orElse(null);
            room.setId(roomId);
        }
        if (roomId == null) {
            return;
        }

        List<Bed> existingBeds = bedRepository.findByRoomIdOrderByBedNumberAsc(roomId);
        int currentBedCount = existingBeds.size();

        if (currentBedCount < targetOccupancy) {
            List<Bed> toAdd = new ArrayList<>();
            for (int i = currentBedCount + 1; i <= targetOccupancy; i++) {
                toAdd.add(Bed.builder()
                        .roomId(roomId)
                        .bedNumber("Bed " + i)
                        .baseRent(rent)
                        .status("AVAILABLE")
                        .build());
            }
            bedRepository.saveAll(toAdd);
        } else if (currentBedCount > targetOccupancy) {
            int bedsToRemove = currentBedCount - targetOccupancy;
            List<Bed> reversed = new ArrayList<>(existingBeds);
            Collections.reverse(reversed);

            for (Bed bed : reversed) {
                if (bedsToRemove <= 0) break;
                if ("AVAILABLE".equalsIgnoreCase(bed.getStatus())) {
                    bedRepository.delete(bed);
                    bedsToRemove--;
                }
            }
        }
    }

    private Long resolveOrCreateFloorId(Long propertyId, String floorStr) {
        return resolveOrCreateFloorId(propertyId, floorStr, null);
    }

    private Long resolveOrCreateFloorId(Long propertyId, String floorStr, Long explicitBlockId) {
        if (propertyId == null) return null;

        Block block = null;
        if (explicitBlockId != null) {
            block = blockRepository.findById(explicitBlockId).orElse(null);
        }

        if (block == null) {
            Property property = propertyRepository.findById(propertyId).orElse(null);
            String blockCode = "BLK-" + (property != null && property.getCode() != null ? property.getCode() : propertyId);
            final Long effectivePropId = propertyId;

            block = blockRepository.findByPropertyIdAndCode(propertyId, blockCode)
                    .orElseGet(() -> {
                        Block newBlock = Block.builder()
                                .propertyId(effectivePropId)
                                .name("Main Block")
                                .code(blockCode)
                                .status("ACTIVE")
                                .build();
                        return blockRepository.save(newBlock);
                    });
        }

        int floorNum = 1;
        String floorName = "Floor 1";
        if (floorStr != null && !floorStr.trim().isEmpty()) {
            String clean = floorStr.trim();
            if (clean.matches("^-?[0-9]+$")) {
                floorNum = Integer.parseInt(clean);
                floorName = "Floor " + floorNum;
            } else if (clean.equalsIgnoreCase("g") || clean.equalsIgnoreCase("ground") || clean.equalsIgnoreCase("ground floor")) {
                floorNum = 0;
                floorName = "Ground Floor";
            } else if (clean.equalsIgnoreCase("b") || clean.equalsIgnoreCase("basement")) {
                floorNum = -1;
                floorName = "Basement";
            } else {
                floorName = "Floor " + clean;
            }
        }

        final int targetFloorNum = floorNum;
        final String targetFloorName = floorName;
        final Long blockId = block.getId();

        Floor floor = floorRepository.findByBlockIdAndFloorNumber(block.getId(), targetFloorNum)
                .orElseGet(() -> {
                    Floor newFloor = Floor.builder()
                            .blockId(blockId)
                            .floorNumber(targetFloorNum)
                            .name(targetFloorName)
                            .status("ACTIVE")
                            .build();
                    return floorRepository.save(newFloor);
                });

        return floor.getId();
    }
}