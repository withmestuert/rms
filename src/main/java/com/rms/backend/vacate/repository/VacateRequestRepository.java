package com.rms.backend.vacate.repository;

import com.rms.backend.vacate.entity.VacateRequest;
import com.rms.backend.vacate.entity.VacateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VacateRequestRepository extends JpaRepository<VacateRequest, Long> {

    List<VacateRequest> findByStatusOrderByCreatedAtDesc(VacateStatus status);

    List<VacateRequest> findAllByOrderByCreatedAtDesc();

    List<VacateRequest> findByPropertyIdOrderByCreatedAtDesc(Long propertyId);

    List<VacateRequest> findByTenantUidOrderByCreatedAtDesc(String tenantUid);

    List<VacateRequest> findByRoomNoOrderByCreatedAtDesc(String roomNo);

    Optional<VacateRequest> findByRequestId(String requestId);

    Optional<VacateRequest> findTopByOrderByIdDesc();
}
