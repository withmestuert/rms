package com.rms.backend.billing.controller;

import com.rms.backend.billing.dto.LedgerTransactionRequestDto;
import com.rms.backend.billing.dto.LedgerTransactionResponseDto;
import com.rms.backend.billing.entity.TransactionType;
import com.rms.backend.billing.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LedgerController {

    private final BillingService billingService;

    @GetMapping
    public ResponseEntity<List<LedgerTransactionResponseDto>> getAllTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        List<LedgerTransactionResponseDto> txns = effectivePropId != null
                ? billingService.getAllTransactions(type, effectivePropId)
                : billingService.getAllTransactions(type);
        return ResponseEntity.ok(txns);
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Long>> getRunningBalance() {
        long balance = billingService.getLatestRunningBalance();
        return ResponseEntity.ok(Map.of("runningBalance", balance));
    }

    @PostMapping
    public ResponseEntity<LedgerTransactionResponseDto> recordTransaction(
            @Valid @RequestBody LedgerTransactionRequestDto dto,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : (headerPropId != null ? headerPropId : dto.getPropertyId());
        if (effectivePropId != null) {
            dto.setPropertyId(effectivePropId);
        }
        LedgerTransactionResponseDto created = billingService.recordCustomTransaction(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
