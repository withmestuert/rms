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

    // =========================================================================
    // PARTITION MAINTENANCE & PRUNED RETRIEVAL
    // =========================================================================

    @org.springframework.data.jpa.repository.Query(value = "SELECT create_invoice_monthly_partition(CAST(?1 AS DATE))", nativeQuery = true)
    void ensureMonthlyPartition(java.time.LocalDate targetDate);

    @org.springframework.data.jpa.repository.Query(value = "SELECT auto_provision_invoice_partitions(?1)", nativeQuery = true)
    void autoProvisionPartitions(int monthsAhead);

    @org.springframework.data.jpa.repository.Query("SELECT i FROM Invoice i WHERE i.createdAt >= :startDate AND i.createdAt < :endDate")
    List<Invoice> findByPartitionDateRange(
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDateTime endDate);

    @org.springframework.data.jpa.repository.Query("SELECT i FROM Invoice i WHERE i.propertyId = :propertyId AND i.createdAt >= :startDate AND i.createdAt < :endDate")
    List<Invoice> findByPropertyIdAndPartitionDateRange(
            @org.springframework.data.repository.query.Param("propertyId") Long propertyId,
            @org.springframework.data.repository.query.Param("startDate") java.time.LocalDateTime startDate,
            @org.springframework.data.repository.query.Param("endDate") java.time.LocalDateTime endDate);
}
