package com.rms.backend.rooms.service;

import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.repository.RoomRepository;
import org.springframework.stereotype.Service;
import com.rms.backend.rooms.dto.RoomRequestDto;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.exception.DuplicateResourceException;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public Room createRoom(Room room) {
        return roomRepository.save(room);
    }

    public List<Room> getAllRooms() {
        return roomRepository.findAll();
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

        room.setRoomNo(roomRequestDTO.getRoomNo());
        room.setFloor(roomRequestDTO.getFloor());
        room.setRoomType(roomRequestDTO.getRoomType());
        room.setRentPerMonth(roomRequestDTO.getRentPerMonth());
        room.setOccupancy(roomRequestDTO.getOccupancy());
        room.setCurrentOccupancy(0);
        room.setReservedCapacity(0);
        room.setAvailable(roomRequestDTO.getAvailable() != null ? roomRequestDTO.getAvailable() : true);

        return roomRepository.save(room);
    }

    //Method to delete room
    public void deleteRoom(String roomNo) {

        Room existingRoom = roomRepository.findById(roomNo)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Room not found with room number: " + roomNo
                        )
                );

        roomRepository.delete(existingRoom);
    }
}