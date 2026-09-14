package com.rms.backend.rooms.repository;

import com.rms.backend.rooms.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, String> {

    List<Room> findByPropertyId(Long propertyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.roomNo = :roomNo")
    Optional<Room> findByRoomNoWithLock(@Param("roomNo") String roomNo);
}