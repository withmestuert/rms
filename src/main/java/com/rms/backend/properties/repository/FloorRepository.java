package com.rms.backend.properties.repository;

import com.rms.backend.properties.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FloorRepository extends JpaRepository<Floor, Long> {

    List<Floor> findByBlockId(Long blockId);

    List<Floor> findByBlockIdOrderByFloorNumberAsc(Long blockId);

    List<Floor> findByBlockIdIn(List<Long> blockIds);

    List<Floor> findByBlockIdInOrderByFloorNumberAsc(List<Long> blockIds);

    Optional<Floor> findByBlockIdAndFloorNumber(Long blockId, Integer floorNumber);

    boolean existsByBlockIdAndFloorNumber(Long blockId, Integer floorNumber);
}
