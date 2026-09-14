package com.rms.backend.rooms.service;

import com.rms.backend.admissions.entity.Admission;
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
        return roomRepository.save(room);
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    public List<Room> getRoomsByPropertyId(Long propertyId) {
        if (propertyId == null) {
            return getAllRooms();
        }
        return roomRepository.findByPropertyId(propertyId);
    }

    public Room getRoomByRoomNo(String roomNo) {
        return roomRepository.findById(roomNo)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found with room number: " + roomNo)
                );
    }


    public Room updateRoom(String roomNo, RoomRequestDto roomRequestDTO) {

        Room existingRoom = roomRepository.findById(roomNo)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Room not found with room number: " + roomNo)
                );

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