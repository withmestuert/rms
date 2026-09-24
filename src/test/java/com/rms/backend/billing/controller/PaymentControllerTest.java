package com.rms.backend.billing.controller;

import com.rms.backend.billing.dto.PaymentRequestDto;
import com.rms.backend.billing.dto.PaymentResponseDto;
import com.rms.backend.billing.entity.InvoiceStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BillingService billingService;

    @InjectMocks
    private PaymentController paymentController;

    private PaymentResponseDto samplePaymentDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        samplePaymentDto = PaymentResponseDto.builder()
                .id(1L)
                .paymentReference("PAY-2026-1001")
                .invoiceId(10L)
                .invoiceNumber("INV-OCT202-1001")
                .tenantUid("TNT-101")
                .tenantName("Arjun Sharma")
                .amount(4000)
                .paymentMode("UPI")
                .transactionReference("UPI-12345")
                .idempotencyKey("KEY-123")
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .invoiceTotalAmount(8000)
                .invoicePaidAmount(4000)
                .invoiceRemainingBalance(4000)
                .invoiceStatus(InvoiceStatus.PARTIALLY_PAID)
                .build();
    }

    @Test
    @DisplayName("POST /api/payments - should record payment and return 201")
    void testRecordPayment() throws Exception {
        when(billingService.recordPaymentAuthoritative(any(PaymentRequestDto.class))).thenReturn(samplePaymentDto);

        String json = "{"
                + "\"invoiceId\":10,"
                + "\"tenantUid\":\"TNT-101\","
                + "\"amount\":4000,"
                + "\"paymentMode\":\"UPI\","
                + "\"idempotencyKey\":\"KEY-123\""
                + "}";

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentReference").value("PAY-2026-1001"))
                .andExpect(jsonPath("$.amount").value(4000))
                .andExpect(jsonPath("$.invoiceStatus").value("PARTIALLY_PAID"));
    }

    @Test
    @DisplayName("GET /api/payments - should return 200 with list of payments")
    void testGetAllPayments() throws Exception {
        when(billingService.getAllPayments(any(), any(), any())).thenReturn(List.of(samplePaymentDto));

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentReference").value("PAY-2026-1001"))
                .andExpect(jsonPath("$[0].idempotencyKey").value("KEY-123"));
    }

    @Test
    @DisplayName("GET /api/payments/{id} - should return payment by id")
    void testGetPaymentById() throws Exception {
        when(billingService.getPaymentById(1L)).thenReturn(samplePaymentDto);

        mockMvc.perform(get("/api/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.amount").value(4000));
    }

    @Test
    @DisplayName("GET /api/payments/invoice/{invoiceId} - should return payments for invoice")
    void testGetPaymentsByInvoiceId() throws Exception {
        when(billingService.getPaymentsByInvoiceId(10L)).thenReturn(List.of(samplePaymentDto));

        mockMvc.perform(get("/api/payments/invoice/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceId").value(10));
    }
}
