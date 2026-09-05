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

    long countByRoomNo(String roomNo);
}
