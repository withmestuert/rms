package com.rms.backend.tenants.repository;

import com.rms.backend.tenants.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    boolean existsByAadhaarNo(String aadhaarNo);

    boolean existsByMobileNumber(String mobileNumber);

    Optional<Tenant> findByAadhaarNo(String aadhaarNo);

    Optional<Tenant> findByMobileNumber(String mobileNumber);

    List<Tenant> findByRoomNo(String roomNo);

    List<Tenant> findByPropertyId(Long propertyId);

    long countByRoomNo(String roomNo);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM Tenant t WHERE (t.status IS NULL OR UPPER(t.status) = 'ACTIVE')")
    List<Tenant> findAllActive();

    @org.springframework.data.jpa.repository.Query("SELECT t FROM Tenant t WHERE t.propertyId = :propertyId AND (t.status IS NULL OR UPPER(t.status) = 'ACTIVE')")
    List<Tenant> findActiveByPropertyId(@org.springframework.data.repository.query.Param("propertyId") Long propertyId);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM Tenant t WHERE t.roomNo = :roomNo AND (t.status IS NULL OR UPPER(t.status) = 'ACTIVE')")
    List<Tenant> findActiveByRoomNo(@org.springframework.data.repository.query.Param("roomNo") String roomNo);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(t) FROM Tenant t WHERE t.roomNo = :roomNo AND (t.status IS NULL OR UPPER(t.status) = 'ACTIVE')")
    long countActiveByRoomNo(@org.springframework.data.repository.query.Param("roomNo") String roomNo);
}
