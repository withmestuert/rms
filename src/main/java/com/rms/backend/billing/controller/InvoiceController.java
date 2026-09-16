package com.rms.backend.billing.controller;

import com.rms.backend.billing.dto.*;
import com.rms.backend.billing.entity.InvoiceStatus;
import com.rms.backend.billing.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final BillingService billingService;

    @GetMapping
    public ResponseEntity<List<InvoiceResponseDto>> getAllInvoices(
            @RequestParam(required = false) String monthYear,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        List<InvoiceResponseDto> invoices = effectivePropId != null
                ? billingService.getAllInvoices(monthYear, status, effectivePropId)
                : billingService.getAllInvoices(monthYear, status);
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponseDto> getInvoiceById(@PathVariable Long id) {
        InvoiceResponseDto invoice = billingService.getInvoiceById(id);
        return ResponseEntity.ok(invoice);
    }

    @PostMapping
    public ResponseEntity<InvoiceResponseDto> createInvoice(
            @Valid @RequestBody InvoiceRequestDto dto,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : (headerPropId != null ? headerPropId : dto.getPropertyId());
        if (effectivePropId != null) {
            dto.setPropertyId(effectivePropId);
        }
        InvoiceResponseDto created = billingService.createInvoice(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/generate-cycle")
    public ResponseEntity<CycleGenerationResultDto> generateCycleInvoices(
            @Valid @RequestBody CycleGenerationRequestDto dto) {
        CycleGenerationResultDto result = billingService.generateCycleInvoices(dto);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<InvoiceResponseDto> recordPayment(
            @PathVariable Long id,
            @Valid @RequestBody RecordPaymentDto dto) {
        InvoiceResponseDto updated = billingService.recordPayment(id, dto);
        return ResponseEntity.ok(updated);
    }
}
