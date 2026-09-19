package com.rms.backend.rooms.controller;

import com.rms.backend.common.SecurityConstants;
import com.rms.backend.common.ApiPaths;
import com.rms.backend.rooms.entity.Room;
import com.rms.backend.rooms.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.rms.backend.rooms.dto.RoomRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.API_ROOMS)
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    //DTO managed post method
    @PostMapping
    public ResponseEntity<Room> createRoom(
            @Valid @RequestBody RoomRequestDto roomRequestDTO,
            @RequestHeader(value = SecurityConstants.PROPERTY_HEADER, required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {

        Long effectivePropId = queryPropId != null ? queryPropId : (headerPropId != null ? headerPropId : roomRequestDTO.getPropertyId());
        if (effectivePropId != null) {
            roomRequestDTO.setPropertyId(effectivePropId);
        }

        Room createdRoom = roomService.createRoom(roomRequestDTO);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdRoom);
    }

    @GetMapping
    public List<Room> getAllRooms(
            @RequestHeader(value = SecurityConstants.PROPERTY_HEADER, required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        if (effectivePropId != null) {
            return roomService.getRoomsByPropertyId(effectivePropId);
        }
        return roomService.getAllRooms();
    }

    @GetMapping(ApiPaths.ROOMNO)
    public Room getRoomByRoomNo(@PathVariable String roomNo) {
        return roomService.getRoomByRoomNo(roomNo);
    }

    /*@PutMapping(ApiPaths.ROOMNO)
    public Room updateRoom(
            @PathVariable String roomNo,
            @RequestBody Room room
    ) {
        return roomService.updateRoom(roomNo, room);
    }*/

    //Updates with DTO code for updation
    @PutMapping(ApiPaths.ROOMNO)
    public Room updateRoom(
            @PathVariable String roomNo,
            @Valid @RequestBody RoomRequestDto roomRequestDTO) {

        return roomService.updateRoom(roomNo, roomRequestDTO);
    }
    //DELETE
    @DeleteMapping(ApiPaths.ROOMNO)
    public ResponseEntity<String> deleteRoom(
            @PathVariable String roomNo) {

        roomService.deleteRoom(roomNo);

        return ResponseEntity.ok("Room deleted successfully");
    }
}