package com.rms.backend.admissions.repository;

import com.rms.backend.admissions.entity.Admission;
import com.rms.backend.admissions.entity.AdmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdmissionRepository extends JpaRepository<Admission, Long> {

    Optional<Admission> findByAdmissionNumber(String admissionNumber);

    boolean existsByAdmissionNumber(String admissionNumber);

    List<Admission> findByTenant_Uid(String tenantUid);

    List<Admission> findByRoom_RoomNo(String roomNo);

    List<Admission> findByStatus(AdmissionStatus status);

    long countByTenant_UidAndStatusIn(String tenantUid, List<AdmissionStatus> statuses);

    long countByTenant_UidAndAdmissionNumberNot(String tenantUid, String admissionNumber);

    Optional<Admission> findTopByOrderByIdDesc();
}
