package com.rms.backend.billing.controller;

import com.rms.backend.billing.dto.LedgerTransactionRequestDto;
import com.rms.backend.billing.dto.LedgerTransactionResponseDto;
import com.rms.backend.billing.entity.TransactionType;
import com.rms.backend.billing.service.BillingService;
import com.rms.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LedgerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BillingService billingService;

    @InjectMocks
    private LedgerController ledgerController;

    private LedgerTransactionResponseDto sampleTxn;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ledgerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleTxn = LedgerTransactionResponseDto.builder()
                .id(1L)
                .referenceNumber("TXN-12345678")
                .date("2024-10-03")
                .type(TransactionType.CREDIT)
                .accountHead("Rent Payment")
                .description("Monthly Rent - Room 101 (Arjun Sharma)")
                .tenantOrVendor("Arjun Sharma")
                .amount(8500)
                .paymentMode("UPI")
                .runningBalance(490650L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/ledger - should return 200 and list")
    void testGetAllTransactions() throws Exception {
        when(billingService.getAllTransactions(null)).thenReturn(List.of(sampleTxn));

        mockMvc.perform(get("/api/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].referenceNumber").value("TXN-12345678"))
                .andExpect(jsonPath("$[0].amount").value(8500));
    }

    @Test
    @DisplayName("GET /api/ledger/balance - should return 200 with balance map")
    void testGetRunningBalance() throws Exception {
        when(billingService.getLatestRunningBalance()).thenReturn(490650L);

        mockMvc.perform(get("/api/ledger/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runningBalance").value(490650));
    }

    @Test
    @DisplayName("POST /api/ledger - should return 201 on recording transaction")
    void testRecordTransaction() throws Exception {
        when(billingService.recordCustomTransaction(any(LedgerTransactionRequestDto.class))).thenReturn(sampleTxn);

        String json = "{"
                + "\"type\":\"CREDIT\","
                + "\"accountHead\":\"Rent Payment\","
                + "\"description\":\"Monthly Rent\","
                + "\"tenantOrVendor\":\"Arjun Sharma\","
                + "\"amount\":8500,"
                + "\"paymentMode\":\"UPI\""
                + "}";

        mockMvc.perform(post("/api/ledger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceNumber").value("TXN-12345678"));
    }
}
