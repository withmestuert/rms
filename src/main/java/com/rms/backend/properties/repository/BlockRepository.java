package com.rms.backend.properties.repository;

import com.rms.backend.properties.entity.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    List<Block> findByPropertyId(Long propertyId);

    Optional<Block> findByPropertyIdAndCode(Long propertyId, String code);

    boolean existsByPropertyIdAndCode(Long propertyId, String code);
}
