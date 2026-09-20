package com.rms.backend.properties.repository;

import com.rms.backend.properties.entity.PropertyArchiveSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyArchiveSnapshotRepository extends JpaRepository<PropertyArchiveSnapshot, Long> {

    List<PropertyArchiveSnapshot> findByPropertyIdOrderByArchivedAtDesc(Long propertyId);

    Optional<PropertyArchiveSnapshot> findTopByPropertyIdOrderByArchivedAtDesc(Long propertyId);
}
