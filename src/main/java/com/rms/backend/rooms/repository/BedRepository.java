package com.rms.backend.rooms.repository;
import com.rms.backend.rooms.entity.Bed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BedRepository extends JpaRepository<Bed, Long> {

    List<Bed> findByRoomId(Long roomId);
    List<Bed> findByRoomIdOrderByBedNumberAsc(Long roomId);
    Optional<Bed> findByRoomIdAndBedNumber(Long roomId, String bedNumber);
    long countByRoomId(Long roomId);
    long countByRoomIdAndStatus(Long roomId, String status);
    List<Bed> findByRoomIdAndStatus(Long roomId, String status);
    Optional<Bed> findFirstByRoomIdAndStatusOrderByBedNumberAsc(Long roomId, String status);
    List<Bed> findByRoomIdIn(List<Long> roomIds);
    void deleteByRoomId(Long roomId);
}
