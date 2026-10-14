package com.rms.backend.billing.repository;

import com.rms.backend.billing.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByPaymentReference(String paymentReference);

    List<Payment> findByInvoiceId(Long invoiceId);

    List<Payment> findByTenantUid(String tenantUid);

    List<Payment> findByPropertyId(Long propertyId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
