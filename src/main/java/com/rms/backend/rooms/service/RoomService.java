package com.rms.backend.rooms.service;

import com.rms.backend.common.SecurityConstants;
import com.rms.backend.security.Access;
import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.entity.AdmissionStatus;
import com.rms.backend.admissions.repository.AdmissionRepository;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
import com.rms.backend.tenants.repository.TenantRepository;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rms.backend.rooms.dto.RoomRequestDto;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.exception.DuplicateResourceException;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final AdmissionRepository admissionRepository;
    private final PropertyRepository propertyRepository;

    public RoomService(RoomRepository roomRepository,
                       TenantRepository tenantRepository,
                       AdmissionRepository admissionRepository,
                       PropertyRepository propertyRepository) {
        this.roomRepository = roomRepository;
        this.tenantRepository = tenantRepository;
        this.admissionRepository = admissionRepository;
        this.propertyRepository = propertyRepository;
    }

    public Room createRoom(Room room) {
        Access.write(room.getPropertyId());
        return roomRepository.save(room);
    }

    @Transactional
    public Room syncRoomOccupancy(Room room) {
        if (room == null) return null;
        Access.read(room.getPropertyId());
        if (SecurityConstants.SUB_MEMBER.equals(Access.current().role())) return room;
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
        boolean shouldBeAvailable = (room.getCurrentOccupancy() + room.getReservedCapacity()) < capacity;
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
                .filter(room -> Access.canAccess(room.getPropertyId()))
                .map(this::syncRoomOccupancy)
                .toList();
    }

    public List<Room> getRoomsByPropertyId(Long propertyId) {
        if (propertyId != null) Access.read(propertyId);
        List<Room> rooms = propertyId == null
                ? roomRepository.findAll()
                : roomRepository.findByPropertyId(propertyId);
        return rooms.stream()
                .filter(room -> Access.canAccess(room.getPropertyId()))
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


    public Room updateRoom(String roomNo, RoomRequestDto roomRequestDTO) {

        Room existingRoom = roomRepository.findById(roomNo)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found with room number: " + roomNo)
                );

        Access.write(existingRoom.getPropertyId());
        Access.matchingProperty(roomRequestDTO.getPropertyId(), existingRoom.getPropertyId());
        existingRoom.setFloor(roomRequestDTO.getFloor());
        existingRoom.setRoomType(roomRequestDTO.getRoomType());
        existingRoom.setRentPerMonth(roomRequestDTO.getRentPerMonth());
        existingRoom.setOccupancy(roomRequestDTO.getOccupancy());
        existingRoom.setAvailable(roomRequestDTO.getAvailable());

        return roomRepository.save(existingRoom);
    }

    //Request to create room via DTO
    public Room createRoom(RoomRequestDto roomRequestDTO) {

        if (roomRepository.existsById(roomRequestDTO.getRoomNo())) {
            throw new DuplicateResourceException(
                    "Room already exists with room number: "
                            + roomRequestDTO.getRoomNo()
            );
        }

        Room room = new Room();

        Long propId = roomRequestDTO.getPropertyId();
        if (propId == null) throw new IllegalArgumentException("Property ID is required");
        Access.write(propId);

        room.setRoomNo(roomRequestDTO.getRoomNo());
        room.setFloor(roomRequestDTO.getFloor());
        room.setRoomType(roomRequestDTO.getRoomType());
        room.setRentPerMonth(roomRequestDTO.getRentPerMonth());
        room.setOccupancy(roomRequestDTO.getOccupancy());
        room.setPropertyId(propId);
        room.setCurrentOccupancy(0);
        room.setReservedCapacity(0);
        room.setAvailable(roomRequestDTO.getAvailable() != null ? roomRequestDTO.getAvailable() : true);

        return roomRepository.save(room);
    }

    //Method to delete room
    @Transactional
    public void deleteRoom(String roomNo) {

        Room existingRoom = roomRepository.findById(roomNo)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Room not found with room number: " + roomNo
                        )
                );

        Access.write(existingRoom.getPropertyId());

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

        roomRepository.delete(existingRoom);
    }
}