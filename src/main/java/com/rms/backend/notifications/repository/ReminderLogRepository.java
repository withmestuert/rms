package com.rms.backend.notifications.repository;

import com.rms.backend.notifications.entity.ReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReminderLogRepository extends JpaRepository<ReminderLog, Long> {

    List<ReminderLog> findByInvoiceIdOrderBySentAtDesc(Long invoiceId);

    List<ReminderLog> findByTenantUidOrderBySentAtDesc(String tenantUid);

    List<ReminderLog> findByPropertyIdOrderBySentAtDesc(Long propertyId);

    List<ReminderLog> findBySentAtAfterOrderBySentAtDesc(LocalDateTime afterDate);

    long countByPropertyIdAndSentAtAfter(Long propertyId, LocalDateTime afterDate);

    long countBySentAtAfter(LocalDateTime afterDate);

    boolean existsByInvoiceIdAndSentAtAfter(Long invoiceId, LocalDateTime afterDate);
}
