package com.rms.backend.properties.repository;

import com.rms.backend.properties.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {
    java.util.List<Property> findByOwnerId(Long ownerId);

    boolean existsByName(String name);

    boolean existsByCode(String code);

    Optional<Property> findByCode(String code);

    List<Property> findByStatus(String status);
}
