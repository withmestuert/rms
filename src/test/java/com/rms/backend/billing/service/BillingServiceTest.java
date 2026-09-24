package com.rms.backend.billing.service;

import com.rms.backend.billing.dto.*;
import com.rms.backend.billing.entity.*;
import com.rms.backend.billing.repository.InvoiceRepository;
import com.rms.backend.billing.repository.LedgerTransactionRepository;
import com.rms.backend.billing.repository.PaymentRepository;
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

    @Mock
    private PaymentRepository paymentRepository;

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

        when(tenantRepository.findAllActive()).thenReturn(List.of(sampleTenant));
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
    @DisplayName("Should record payment, save Payment entity, and atomically write to ledger")
    void testRecordPayment() {
        RecordPaymentDto payReq = RecordPaymentDto.builder()
                .paymentMode("UPI")
                .transactionRef("UPI-998877")
                .paidOn("2024-10-03")
                .build();

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(501L);
            return p;
        });
        when(ledgerRepository.findTopByOrderByCreatedAtDescIdDesc()).thenReturn(Optional.of(LedgerTransaction.builder().runningBalance(482150L).build()));
        when(ledgerRepository.save(any(LedgerTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        InvoiceResponseDto result = billingService.recordPayment(1L, payReq);

        assertNotNull(result);
        assertEquals(InvoiceStatus.PAID, result.getStatus());
        assertEquals("UPI", result.getPaymentMode());
        assertEquals("UPI-998877", result.getTransactionRef());
        assertEquals(8500, result.getPaidAmount());
        assertEquals(0, result.getRemainingBalance());

        verify(paymentRepository).save(any(Payment.class));
        verify(invoiceRepository).save(any(Invoice.class));
        verify(ledgerRepository).save(argThat(txn ->
                txn.getType() == TransactionType.CREDIT &&
                txn.getAmount() == 8500 &&
                txn.getRunningBalance() == 482150L + 8500 &&
                "TNT-101".equals(txn.getTenantUid()) &&
                Long.valueOf(1L).equals(txn.getInvoiceId()) &&
                Long.valueOf(501L).equals(txn.getPaymentId())
        ));
    }

    @Test
    @DisplayName("Should record partial payment and update status to PARTIALLY_PAID")
    void testRecordPartialPayment() {
        RecordPaymentDto payReq = RecordPaymentDto.builder()
                .paymentMode("UPI")
                .transactionRef("UPI-PARTIAL-1")
                .paidOn("2024-10-03")
                .amount(3000)
                .build();

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(502L);
            return p;
        });
        when(ledgerRepository.findTopByOrderByCreatedAtDescIdDesc()).thenReturn(Optional.of(LedgerTransaction.builder().runningBalance(10000L).build()));
        when(ledgerRepository.save(any(LedgerTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        InvoiceResponseDto result = billingService.recordPayment(1L, payReq);

        assertNotNull(result);
        assertEquals(InvoiceStatus.PARTIALLY_PAID, result.getStatus());
        assertEquals(3000, result.getPaidAmount());
        assertEquals(5500, result.getRemainingBalance());

        verify(paymentRepository).save(any(Payment.class));
        verify(ledgerRepository).save(argThat(txn ->
                txn.getType() == TransactionType.CREDIT &&
                txn.getAmount() == 3000 &&
                txn.getRunningBalance() == 13000L
        ));
    }

    @Test
    @DisplayName("Should settle invoice with second partial payment to PAID")
    void testSecondPartialPaymentSettlement() {
        sampleInvoice.setPaidAmount(3000);
        sampleInvoice.setStatus(InvoiceStatus.PARTIALLY_PAID);

        PaymentRequestDto payReq = PaymentRequestDto.builder()
                .invoiceId(1L)
                .tenantUid("TNT-101")
                .amount(5500)
                .paymentMode("CASH")
                .idempotencyKey("IDEM-SETTLE-1")
                .build();

        when(paymentRepository.findByIdempotencyKey("IDEM-SETTLE-1")).thenReturn(Optional.empty());
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(503L);
            return p;
        });
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerRepository.findTopByOrderByCreatedAtDescIdDesc()).thenReturn(Optional.of(LedgerTransaction.builder().runningBalance(13000L).build()));
        when(ledgerRepository.save(any(LedgerTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto result = billingService.recordPaymentAuthoritative(payReq);

        assertNotNull(result);
        assertEquals(InvoiceStatus.PAID, result.getInvoiceStatus());
        assertEquals(8500, result.getInvoicePaidAmount());
        assertEquals(0, result.getInvoiceRemainingBalance());
        assertEquals("SUCCESS", result.getStatus());

        verify(paymentRepository).save(any(Payment.class));
        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Critical Idempotency Test: Submitting same key twice must return existing payment and make 0 duplicate mutations")
    void testRecordPaymentAuthoritative_Idempotency() {
        Payment existingPayment = Payment.builder()
                .id(777L)
                .paymentReference("PAY-2026-777")
                .invoiceId(1L)
                .tenantUid("TNT-101")
                .amount(4000)
                .paymentMode("UPI")
                .idempotencyKey("IDEM-REPLAY-KEY-999")
                .status("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();

        PaymentRequestDto payReq = PaymentRequestDto.builder()
                .invoiceId(1L)
                .tenantUid("TNT-101")
                .amount(4000)
                .paymentMode("UPI")
                .idempotencyKey("IDEM-REPLAY-KEY-999")
                .build();

        when(paymentRepository.findByIdempotencyKey("IDEM-REPLAY-KEY-999")).thenReturn(Optional.of(existingPayment));
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));

        PaymentResponseDto result = billingService.recordPaymentAuthoritative(payReq);

        assertNotNull(result);
        assertEquals(777L, result.getId());
        assertEquals("PAY-2026-777", result.getPaymentReference());
        assertEquals("IDEM-REPLAY-KEY-999", result.getIdempotencyKey());

        // Zero additional writes to payment repository or ledger repository!
        verify(paymentRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject payment exceeding outstanding balance")
    void testRecordPayment_ExceedingAmount() {
        RecordPaymentDto payReq = RecordPaymentDto.builder()
                .paymentMode("UPI")
                .amount(9000) // sampleInvoice amount is 8500
                .build();

        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));

        assertThrows(IllegalArgumentException.class, () -> billingService.recordPayment(1L, payReq));
        verify(paymentRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject paying already paid invoice")
    void testRecordPayment_AlreadyPaid() {
        sampleInvoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));

        RecordPaymentDto payReq = RecordPaymentDto.builder().paymentMode("Cash").build();

        assertThrows(DuplicateResourceException.class, () -> billingService.recordPayment(1L, payReq));
        verify(paymentRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject payment if tenant UID does not match invoice tenant")
    void testPaymentTenantMismatchThrows() {
        PaymentRequestDto payReq = PaymentRequestDto.builder()
                .invoiceId(1L)
                .tenantUid("TNT-WRONG-TENANT")
                .amount(2000)
                .paymentMode("UPI")
                .idempotencyKey("IDEM-MISMATCH")
                .build();

        when(paymentRepository.findByIdempotencyKey("IDEM-MISMATCH")).thenReturn(Optional.empty());
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(sampleInvoice));

        assertThrows(IllegalArgumentException.class, () -> billingService.recordPaymentAuthoritative(payReq));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should query transactions by tenant UID")
    void testGetTransactionsByTenant() {
        LedgerTransaction txn = LedgerTransaction.builder()
                .id(1L)
                .referenceNumber("TXN-100")
                .tenantUid("TNT-101")
                .amount(8500)
                .type(TransactionType.CREDIT)
                .accountHead("Rent Payment")
                .description("Rent")
                .tenantOrVendor("Arjun Sharma")
                .paymentMode("UPI")
                .date("2026-09-20")
                .runningBalance(8500L)
                .build();

        when(ledgerRepository.findByTenantUidOrderByCreatedAtDescIdDesc("TNT-101")).thenReturn(List.of(txn));

        List<LedgerTransactionResponseDto> result = billingService.getTransactionsByTenant("TNT-101");
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("TNT-101", result.get(0).getTenantUid());
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

        when(ledgerRepository.findTopByOrderByCreatedAtDescIdDesc()).thenReturn(Optional.of(LedgerTransaction.builder().runningBalance(482150L).build()));
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
