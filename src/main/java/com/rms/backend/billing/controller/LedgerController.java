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
            @RequestParam(required = false) TransactionType type) {
        List<LedgerTransactionResponseDto> txns = billingService.getAllTransactions(type);
        return ResponseEntity.ok(txns);
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Long>> getRunningBalance() {
        long balance = billingService.getLatestRunningBalance();
        return ResponseEntity.ok(Map.of("runningBalance", balance));
    }

    @PostMapping
    public ResponseEntity<LedgerTransactionResponseDto> recordTransaction(
            @Valid @RequestBody LedgerTransactionRequestDto dto) {
        LedgerTransactionResponseDto created = billingService.recordCustomTransaction(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
