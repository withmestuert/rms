package com.rms.backend.billing.controller;

import com.rms.backend.billing.dto.*;
import com.rms.backend.billing.entity.InvoiceStatus;
import com.rms.backend.billing.service.BillingService;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.GlobalExceptionHandler;
import com.rms.backend.exception.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InvoiceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BillingService billingService;

    @InjectMocks
    private InvoiceController invoiceController;

    private InvoiceResponseDto sampleDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(invoiceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleDto = InvoiceResponseDto.builder()
                .id(1L)
                .invoiceNumber("INV-OCT202-1001")
                .tenantUid("TNT-101")
                .tenantName("Arjun Sharma")
                .roomNo("101")
                .monthYear("October 2024")
                .amount(8500)
                .dueDate("2024-10-05")
                .status(InvoiceStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/invoices - should return 200 and list")
    void testGetAllInvoices() throws Exception {
        when(billingService.getAllInvoices(null, null)).thenReturn(List.of(sampleDto));

        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceNumber").value("INV-OCT202-1001"))
                .andExpect(jsonPath("$[0].amount").value(8500));
    }

    @Test
    @DisplayName("GET /api/invoices/{id} - should return 200 when found")
    void testGetInvoiceById() throws Exception {
        when(billingService.getInvoiceById(1L)).thenReturn(sampleDto);

        mockMvc.perform(get("/api/invoices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tenantName").value("Arjun Sharma"));
    }

    @Test
    @DisplayName("GET /api/invoices/{id} - should return 404 when not found")
    void testGetInvoiceById_NotFound() throws Exception {
        when(billingService.getInvoiceById(99L)).thenThrow(new ResourceNotFoundException("Invoice not found"));

        mockMvc.perform(get("/api/invoices/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/invoices - should return 201 on success")
    void testCreateInvoice() throws Exception {
        when(billingService.createInvoice(any(InvoiceRequestDto.class))).thenReturn(sampleDto);

        String json = "{"
                + "\"tenantUid\":\"TNT-101\","
                + "\"monthYear\":\"October 2024\","
                + "\"amount\":8500,"
                + "\"dueDate\":\"2024-10-05\""
                + "}";

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-OCT202-1001"));
    }

    @Test
    @DisplayName("POST /api/invoices - should return 409 on duplicate")
    void testCreateInvoice_Duplicate() throws Exception {
        when(billingService.createInvoice(any(InvoiceRequestDto.class)))
                .thenThrow(new DuplicateResourceException("Invoice already exists"));

        String json = "{"
                + "\"tenantUid\":\"TNT-101\","
                + "\"monthYear\":\"October 2024\","
                + "\"amount\":8500,"
                + "\"dueDate\":\"2024-10-05\""
                + "}";

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/invoices/generate-cycle - should return 200 with result")
    void testGenerateCycleInvoices() throws Exception {
        CycleGenerationResultDto result = CycleGenerationResultDto.builder()
                .generatedCount(3)
                .skippedCount(0)
                .totalAmount(25500)
                .generatedInvoices(List.of(sampleDto))
                .build();

        when(billingService.generateCycleInvoices(any(CycleGenerationRequestDto.class))).thenReturn(result);

        String json = "{"
                + "\"monthYear\":\"November 2024\","
                + "\"dueDate\":\"2024-11-05\""
                + "}";

        mockMvc.perform(post("/api/invoices/generate-cycle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedCount").value(3))
                .andExpect(jsonPath("$.totalAmount").value(25500));
    }

    @Test
    @DisplayName("POST /api/invoices/{id}/pay - should return 200 on payment")
    void testRecordPayment() throws Exception {
        sampleDto.setStatus(InvoiceStatus.PAID);
        sampleDto.setPaymentMode("UPI");
        sampleDto.setTransactionRef("UPI-12345");

        when(billingService.recordPayment(eq(1L), any(RecordPaymentDto.class))).thenReturn(sampleDto);

        String json = "{"
                + "\"paymentMode\":\"UPI\","
                + "\"transactionRef\":\"UPI-12345\""
                + "}";

        mockMvc.perform(post("/api/invoices/1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentMode").value("UPI"));
    }
}
