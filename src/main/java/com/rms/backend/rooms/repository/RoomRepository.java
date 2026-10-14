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

    Optional<Room> findByPropertyIdAndRoomNo(Long propertyId, String roomNo);

    boolean existsByPropertyIdAndRoomNo(Long propertyId, String roomNo);

    Optional<Room> findFirstById(Long id);

    List<Room> findByFloorId(Long floorId);

    @Query(value = "SELECT id FROM rooms WHERE room_no = :roomNo", nativeQuery = true)
    Optional<Long> findSurrogateIdByRoomNo(@Param("roomNo") String roomNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.roomNo = :roomNo")
    Optional<Room> findByRoomNoWithLock(@Param("roomNo") String roomNo);
}