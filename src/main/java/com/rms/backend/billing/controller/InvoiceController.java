package com.rms.backend.billing.controller;

import com.rms.backend.billing.dto.*;
import com.rms.backend.billing.entity.InvoiceStatus;
import com.rms.backend.billing.service.BillingService;
import com.rms.backend.billing.service.AutoRentInvoiceScheduler;
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
    private final AutoRentInvoiceScheduler autoRentInvoiceScheduler;

    @GetMapping("/auto-cycle/status")
    public ResponseEntity<java.util.Map<String, Object>> getAutoCycleStatus() {
        return ResponseEntity.ok(autoRentInvoiceScheduler.getSchedulerStatus());
    }

    @PostMapping("/auto-cycle/trigger")
    public ResponseEntity<CycleGenerationResultDto> triggerAutoCycle(
            @RequestParam(required = false) String monthYear,
            @RequestParam(required = false) Integer dueDay,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        java.time.LocalDate targetDate = java.time.LocalDate.now();
        if (monthYear != null && !monthYear.isBlank()) {
            // Attempt to parse if "YYYY-MM" or fallback to current date
            try {
                if (monthYear.matches("\\d{4}-\\d{2}")) {
                    targetDate = java.time.YearMonth.parse(monthYear).atDay(1);
                }
            } catch (Exception ignored) {}
        }
        CycleGenerationResultDto result = autoRentInvoiceScheduler.executeAutoCycle(targetDate, dueDay, effectivePropId);
        return ResponseEntity.ok(result);
    }

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

    @GetMapping("/partitioned")
    public ResponseEntity<List<InvoiceResponseDto>> getInvoicesPartitioned(
            @RequestParam String monthYear,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        List<InvoiceResponseDto> invoices = billingService.getInvoicesByPartitionPruning(monthYear, effectivePropId);
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/tenant-monthly-ledger")
    public ResponseEntity<List<TenantMonthlyPayItemDto>> getTenantMonthlyLedger(
            @RequestParam(required = false) String tenantUid,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String monthYear,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        List<TenantMonthlyPayItemDto> ledger = billingService.getTenantMonthlyPayList(effectivePropId, tenantUid, search, monthYear);
        return ResponseEntity.ok(ledger);
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
            @Valid @RequestBody CycleGenerationRequestDto dto,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : (headerPropId != null ? headerPropId : dto.getPropertyId());
        if (effectivePropId != null) {
            dto.setPropertyId(effectivePropId);
        }
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
