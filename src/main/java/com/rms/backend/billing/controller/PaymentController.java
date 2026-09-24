package com.rms.backend.billing.controller;

import com.rms.backend.billing.dto.PaymentRequestDto;
import com.rms.backend.billing.dto.PaymentResponseDto;
import com.rms.backend.billing.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final BillingService billingService;

    @PostMapping
    public ResponseEntity<PaymentResponseDto> recordPayment(
            @Valid @RequestBody PaymentRequestDto dto,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : (headerPropId != null ? headerPropId : dto.getPropertyId());
        if (effectivePropId != null) {
            dto.setPropertyId(effectivePropId);
        }
        PaymentResponseDto response = billingService.recordPaymentAuthoritative(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponseDto>> getAllPayments(
            @RequestParam(required = false) Long invoiceId,
            @RequestParam(required = false) String tenantUid,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        List<PaymentResponseDto> payments = billingService.getAllPayments(effectivePropId, tenantUid, invoiceId);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDto> getPaymentById(@PathVariable Long id) {
        PaymentResponseDto payment = billingService.getPaymentById(id);
        return ResponseEntity.ok(payment);
    }

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<List<PaymentResponseDto>> getPaymentsByInvoiceId(@PathVariable Long invoiceId) {
        List<PaymentResponseDto> payments = billingService.getPaymentsByInvoiceId(invoiceId);
        return ResponseEntity.ok(payments);
    }
}
