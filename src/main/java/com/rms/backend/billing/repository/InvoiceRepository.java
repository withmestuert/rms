package com.rms.backend.billing.repository;

import com.rms.backend.billing.entity.Invoice;
import com.rms.backend.billing.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByMonthYear(String monthYear);

    List<Invoice> findByTenantUid(String tenantUid);

    List<Invoice> findByStatus(InvoiceStatus status);

    List<Invoice> findByMonthYearAndStatus(String monthYear, InvoiceStatus status);

    List<Invoice> findByPropertyId(Long propertyId);

    boolean existsByTenantUidAndMonthYear(String tenantUid, String monthYear);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
