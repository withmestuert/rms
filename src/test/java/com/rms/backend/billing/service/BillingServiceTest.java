package com.rms.backend.billing.service;

import com.rms.backend.billing.dto.*;
import com.rms.backend.billing.entity.Invoice;
import com.rms.backend.billing.entity.InvoiceStatus;
import com.rms.backend.billing.entity.LedgerTransaction;
import com.rms.backend.billing.entity.TransactionType;
import com.rms.backend.billing.repository.InvoiceRepository;
import com.rms.backend.billing.repository.LedgerTransactionRepository;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.tenants.entity.AdvancePaidStatus;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private LedgerTransactionRepository ledgerRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private BillingService billingService;

    private Tenant sampleTenant;
    private Invoice sampleInvoice;

    @BeforeEach
    void setUp() {
        sampleTenant = new Tenant(
                "TNT-101", "Arjun Sharma", "123456789012", "9876543210",
                "Working", "Google", "9876543211", "101", 8500, AdvancePaidStatus.PAID, 8500
        );

        sampleInvoice = Invoice.builder()
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
    @DisplayName("Should return all invoices without filters")
    void testGetAllInvoices() {
        when(invoiceRepository.findAll()).thenReturn(List.of(sampleInvoice));

        List<InvoiceResponseDto> result = billingService.getAllInvoices(null, null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("INV-OCT202-1001", result.get(0).getInvoiceNumber());
        verify(invoiceRepository).findAll();
    }

    @Test
    @DisplayName("Should return invoices with monthYear filter")
    void testGetAllInvoicesWithMonthYear() {
        when(invoiceRepository.findByMonthYear("October 2024")).thenReturn(List.of(sampleInvoice));

        List<InvoiceResponseDto> result = billingService.getAllInvoices("October 2024", null);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(invoiceRepository).findByMonthYear("October 2024");
    }

    @Test
    @DisplayName("Should get invoice by ID")
    void testGetInvoiceById() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));

        InvoiceResponseDto result = billingService.getInvoiceById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(8500, result.getAmount());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for unknown invoice ID")
    void testGetInvoiceById_NotFound() {
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> billingService.getInvoiceById(99L));
    }

    @Test
    @DisplayName("Should create single invoice successfully")
    void testCreateInvoice() {
        InvoiceRequestDto req = InvoiceRequestDto.builder()
                .tenantUid("TNT-101")
                .monthYear("October 2024")
                .amount(8500)
                .dueDate("2024-10-05")
                .build();

        when(tenantRepository.findById("TNT-101")).thenReturn(Optional.of(sampleTenant));
        when(invoiceRepository.existsByTenantUidAndMonthYear("TNT-101", "October 2024")).thenReturn(false);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(10L);
            return i;
        });

        InvoiceResponseDto result = billingService.createInvoice(req);

        assertNotNull(result);
        assertEquals("TNT-101", result.getTenantUid());
        assertEquals("Arjun Sharma", result.getTenantName());
        assertEquals(InvoiceStatus.PENDING, result.getStatus());
        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException if invoice already exists for month")
    void testCreateInvoice_Duplicate() {
        InvoiceRequestDto req = InvoiceRequestDto.builder()
                .tenantUid("TNT-101")
                .monthYear("October 2024")
                .amount(8500)
                .dueDate("2024-10-05")
                .build();

        when(tenantRepository.findById("TNT-101")).thenReturn(Optional.of(sampleTenant));
        when(invoiceRepository.existsByTenantUidAndMonthYear("TNT-101", "October 2024")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> billingService.createInvoice(req));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should generate cycle invoices in batch")
    void testGenerateCycleInvoices() {
        CycleGenerationRequestDto req = CycleGenerationRequestDto.builder()
                .monthYear("November 2024")
                .dueDate("2024-11-05")
                .build();

        when(tenantRepository.findAll()).thenReturn(List.of(sampleTenant));
        when(invoiceRepository.existsByTenantUidAndMonthYear("TNT-101", "November 2024")).thenReturn(false);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setId(20L);
            return i;
        });

        CycleGenerationResultDto result = billingService.generateCycleInvoices(req);

        assertNotNull(result);
        assertEquals(1, result.getGeneratedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(8500, result.getTotalAmount());
        assertEquals(1, result.getGeneratedInvoices().size());
    }

    @Test
    @DisplayName("Should record payment and atomically write to ledger")
    void testRecordPayment() {
        RecordPaymentDto payReq = RecordPaymentDto.builder()
                .paymentMode("UPI")
                .transactionRef("UPI-998877")
                .paidOn("2024-10-03")
                .build();

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerRepository.findTopByOrderByCreatedAtDescIdDesc()).thenReturn(Optional.empty()); // default 482150
        when(ledgerRepository.save(any(LedgerTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        InvoiceResponseDto result = billingService.recordPayment(1L, payReq);

        assertNotNull(result);
        assertEquals(InvoiceStatus.PAID, result.getStatus());
        assertEquals("UPI", result.getPaymentMode());
        assertEquals("UPI-998877", result.getTransactionRef());

        verify(invoiceRepository).save(any(Invoice.class));
        verify(ledgerRepository).save(argThat(txn ->
                txn.getType() == TransactionType.CREDIT &&
                txn.getAmount() == 8500 &&
                txn.getRunningBalance() == 482150L + 8500
        ));
    }

    @Test
    @DisplayName("Should reject paying already paid invoice")
    void testRecordPayment_AlreadyPaid() {
        sampleInvoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));

        RecordPaymentDto payReq = RecordPaymentDto.builder().paymentMode("Cash").build();

        assertThrows(DuplicateResourceException.class, () -> billingService.recordPayment(1L, payReq));
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should record custom debit transaction in ledger")
    void testRecordCustomTransaction_Debit() {
        LedgerTransactionRequestDto req = LedgerTransactionRequestDto.builder()
                .type(TransactionType.DEBIT)
                .accountHead("Maintenance Expense")
                .description("Plumbing Repairs")
                .tenantOrVendor("City Plumbers")
                .amount(2500)
                .paymentMode("Cash")
                .date("2024-10-04")
                .build();

        when(ledgerRepository.findTopByOrderByCreatedAtDescIdDesc()).thenReturn(Optional.empty()); // 482150
        when(ledgerRepository.save(any(LedgerTransaction.class))).thenAnswer(inv -> {
            LedgerTransaction t = inv.getArgument(0);
            t.setId(101L);
            return t;
        });

        LedgerTransactionResponseDto result = billingService.recordCustomTransaction(req);

        assertNotNull(result);
        assertEquals(TransactionType.DEBIT, result.getType());
        assertEquals(2500, result.getAmount());
        assertEquals(482150L - 2500, result.getRunningBalance());
        verify(ledgerRepository).save(any(LedgerTransaction.class));
    }
}
