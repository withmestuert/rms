package com.rms.backend.billing.repository;

import com.rms.backend.billing.entity.GatewayOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GatewayOrderRepository extends JpaRepository<GatewayOrder, Long> {

    Optional<GatewayOrder> findByOrderId(String orderId);

    Optional<GatewayOrder> findByRazorpayPaymentId(String razorpayPaymentId);

    List<GatewayOrder> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    List<GatewayOrder> findByInvoiceNumberOrderByCreatedAtDesc(String invoiceNumber);
}
