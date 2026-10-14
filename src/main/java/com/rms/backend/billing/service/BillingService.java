package com.rms.backend.billing.service;

import com.rms.backend.billing.dto.*;
import com.rms.backend.billing.entity.*;
import com.rms.backend.billing.repository.InvoiceRepository;
import com.rms.backend.billing.repository.LedgerTransactionRepository;
import com.rms.backend.billing.repository.PaymentRepository;
import com.rms.backend.exception.DuplicateResourceException;
import com.rms.backend.exception.ResourceNotFoundException;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final LedgerTransactionRepository ledgerRepository;
    private final TenantRepository tenantRepository;
    private final PaymentRepository paymentRepository;

    private static final AtomicLong INVOICE_SEQ = new AtomicLong(1001);
    private static final AtomicLong PAYMENT_SEQ = new AtomicLong(1001);

    // =========================================================================
    // INVOICES
    // =========================================================================

    @Transactional(readOnly = true)
    public List<InvoiceResponseDto> getAllInvoices(String monthYear, InvoiceStatus status) {
        return getAllInvoices(monthYear, status, null);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponseDto> getAllInvoices(String monthYear, InvoiceStatus status, Long propertyId) {
        List<Invoice> invoices;
        if (propertyId != null) {
            invoices = invoiceRepository.findByPropertyId(propertyId);
            if (monthYear != null && !monthYear.isBlank()) {
                invoices = invoices.stream().filter(i -> monthYear.trim().equalsIgnoreCase(i.getMonthYear())).toList();
            }
            if (status != null) {
                invoices = invoices.stream().filter(i -> status == i.getStatus()).toList();
            }
        } else if (monthYear != null && !monthYear.isBlank() && status != null) {
            invoices = invoiceRepository.findByMonthYearAndStatus(monthYear.trim(), status);
        } else if (monthYear != null && !monthYear.isBlank()) {
            invoices = invoiceRepository.findByMonthYear(monthYear.trim());
        } else if (status != null) {
            invoices = invoiceRepository.findByStatus(status);
        } else {
            invoices = invoiceRepository.findAll();
        }
        return invoices.stream().map(this::toInvoiceResponseDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvoiceResponseDto getInvoiceById(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with ID: " + id));
        return toInvoiceResponseDto(invoice);
    }

    @Transactional
    public InvoiceResponseDto createInvoice(InvoiceRequestDto dto) {
        Tenant tenant = tenantRepository.findById(dto.getTenantUid())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with UID: " + dto.getTenantUid()));

        if (invoiceRepository.existsByTenantUidAndMonthYear(tenant.getUid(), dto.getMonthYear().trim())) {
            throw new DuplicateResourceException(
                    "Invoice already exists for tenant " + tenant.getName() + " for " + dto.getMonthYear());
        }

        String invoiceNumber = generateInvoiceNumber(dto.getMonthYear());

        Long propId = dto.getPropertyId() != null ? dto.getPropertyId() : tenant.getPropertyId();

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .tenantUid(tenant.getUid())
                .tenantName(tenant.getName())
                .roomNo(tenant.getRoomNo())
                .propertyId(propId)
                .monthYear(dto.getMonthYear().trim())
                .amount(dto.getAmount())
                .dueDate(dto.getDueDate().trim())
                .status(InvoiceStatus.PENDING)
                .paymentMode(dto.getPaymentMode())
                .createdAt(LocalDateTime.now())
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Created invoice: {} for tenant: {}", saved.getInvoiceNumber(), saved.getTenantName());
        return toInvoiceResponseDto(saved);
    }

    @Transactional
    public CycleGenerationResultDto generateCycleInvoices(CycleGenerationRequestDto dto) {
        List<Tenant> tenants = dto.getPropertyId() != null
                ? tenantRepository.findActiveByPropertyId(dto.getPropertyId())
                : tenantRepository.findAllActive();
        List<InvoiceResponseDto> generated = new ArrayList<>();
        int skippedCount = 0;
        long totalAmount = 0;

        String monthYear = dto.getMonthYear().trim();
        String dueDate = dto.getDueDate().trim();

        for (Tenant tenant : tenants) {
            if (invoiceRepository.existsByTenantUidAndMonthYear(tenant.getUid(), monthYear)) {
                skippedCount++;
                continue;
            }

            int rentAmount = tenant.getStandardRent() != null && tenant.getStandardRent() > 0
                    ? tenant.getStandardRent()
                    : 8000;

            String invoiceNumber = generateInvoiceNumber(monthYear);

            Invoice invoice = Invoice.builder()
                    .invoiceNumber(invoiceNumber)
                    .tenantUid(tenant.getUid())
                    .tenantName(tenant.getName())
                    .roomNo(tenant.getRoomNo())
                    .propertyId(tenant.getPropertyId())
                    .monthYear(monthYear)
                    .amount(rentAmount)
                    .paidAmount(0)
                    .dueDate(dueDate)
                    .status(InvoiceStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            Invoice saved = invoiceRepository.save(invoice);
            generated.add(toInvoiceResponseDto(saved));
            totalAmount += rentAmount;
        }

        log.info("Cycle generation complete for {}: Generated {}, Skipped {}", monthYear, generated.size(), skippedCount);

        return CycleGenerationResultDto.builder()
                .generatedCount(generated.size())
                .skippedCount(skippedCount)
                .totalAmount(totalAmount)
                .generatedInvoices(generated)
                .build();
    }

    // =========================================================================
    // AUTHORITATIVE PAYMENTS (PHASE 2)
    // =========================================================================

    @Transactional
    public PaymentResponseDto recordPaymentAuthoritative(PaymentRequestDto dto) {
        String key = (dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank())
                ? dto.getIdempotencyKey().trim()
                : null;

        // 1. Check idempotency guard
        if (key != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                Payment p = existing.get();
                log.info("Idempotent payment request detected for key {}. Returning existing payment result (paymentId: {}).", key, p.getId());
                Invoice inv = invoiceRepository.findById(p.getInvoiceId()).orElse(null);
                return toPaymentResponseDto(p, inv);
            }
        }

        // 2. Validate invoice
        Invoice invoice = invoiceRepository.findById(dto.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with ID: " + dto.getInvoiceId()));

        if (dto.getTenantUid() != null && !dto.getTenantUid().isBlank() && !dto.getTenantUid().trim().equals(invoice.getTenantUid())) {
            throw new IllegalArgumentException("Invoice does not belong to tenant: " + dto.getTenantUid());
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new DuplicateResourceException("Invoice " + invoice.getInvoiceNumber() + " is already marked as PAID");
        }

        int currentPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : 0;
        int outstanding = invoice.getAmount() - currentPaid;

        if (outstanding <= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);
            throw new DuplicateResourceException("Invoice " + invoice.getInvoiceNumber() + " has zero outstanding balance");
        }

        int paymentAmount = (dto.getAmount() != null && dto.getAmount() > 0)
                ? dto.getAmount()
                : outstanding;

        // 3. Overpayment check
        if (paymentAmount > outstanding) {
            throw new IllegalArgumentException("Payment amount (₹" + paymentAmount + ") cannot exceed outstanding balance (₹" + outstanding + ")");
        }

        String effectiveKey = key != null ? key : ("IDEM-" + UUID.randomUUID());
        String payRef = generatePaymentReference();
        String paidDate = dto.getPaidOn() != null && !dto.getPaidOn().isBlank()
                ? dto.getPaidOn().trim()
                : LocalDate.now().toString();

        String txnRef = dto.getTransactionReference() != null && !dto.getTransactionReference().isBlank()
                ? dto.getTransactionReference().trim()
                : payRef;

        // 4. Save Payment entity
        Payment payment = Payment.builder()
                .paymentReference(payRef)
                .invoiceId(invoice.getId())
                .tenantUid(invoice.getTenantUid())
                .amount(paymentAmount)
                .paymentMode(dto.getPaymentMode() != null ? dto.getPaymentMode().trim().toUpperCase() : "CASH")
                .transactionReference(txnRef)
                .idempotencyKey(effectiveKey)
                .status("SUCCESS")
                .propertyId(invoice.getPropertyId() != null ? invoice.getPropertyId() : dto.getPropertyId())
                .createdAt(LocalDateTime.now())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // 5. Update Invoice status & balances
        int newPaid = currentPaid + paymentAmount;
        invoice.setPaidAmount(newPaid);
        if (newPaid >= invoice.getAmount()) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        invoice.setPaidOn(paidDate);
        invoice.setPaymentMode(dto.getPaymentMode());
        invoice.setTransactionRef(txnRef);

        Invoice updatedInvoice = invoiceRepository.save(invoice);

        // 6. Append immutable Ledger record with property isolation and entity linkage
        long currentBalance = getLatestRunningBalance(invoice.getPropertyId());
        long newBalance = currentBalance + paymentAmount;

        LedgerTransaction ledgerTxn = LedgerTransaction.builder()
                .referenceNumber(payRef)
                .date(paidDate)
                .type(TransactionType.CREDIT)
                .accountHead("Rent Payment")
                .description("Rent Payment (" + (updatedInvoice.getStatus() == InvoiceStatus.PAID ? "Full" : "Partial") + ") - Room " + invoice.getRoomNo() + " (" + invoice.getTenantName() + ")")
                .tenantOrVendor(invoice.getTenantName())
                .amount(paymentAmount)
                .paymentMode(dto.getPaymentMode())
                .propertyId(invoice.getPropertyId())
                .runningBalance(newBalance)
                .tenantUid(invoice.getTenantUid())
                .invoiceId(invoice.getId())
                .paymentId(savedPayment.getId())
                .createdAt(LocalDateTime.now())
                .build();

        ledgerRepository.save(ledgerTxn);

        log.info("Recorded payment {} of ₹{} for invoice {}. Status: {}. Idempotency key: {}",
                savedPayment.getPaymentReference(), paymentAmount, invoice.getInvoiceNumber(), updatedInvoice.getStatus(), effectiveKey);

        return toPaymentResponseDto(savedPayment, updatedInvoice);
    }

    @Transactional
    public InvoiceResponseDto recordPayment(Long invoiceId, RecordPaymentDto dto) {
        String key = (dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank())
                ? dto.getIdempotencyKey().trim()
                : (dto.getTransactionRef() != null && !dto.getTransactionRef().isBlank() ? "TXNKEY-" + dto.getTransactionRef().trim() : null);

        // Check if idempotent call already processed
        if (key != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                log.info("Idempotent legacy payment request detected for key {}. Returning existing invoice.", key);
                Invoice existingInv = invoiceRepository.findById(invoiceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with ID: " + invoiceId));
                return toInvoiceResponseDto(existingInv);
            }
        }

        PaymentRequestDto payDto = PaymentRequestDto.builder()
                .invoiceId(invoiceId)
                .amount(dto.getAmount())
                .paymentMode(dto.getPaymentMode())
                .transactionReference(dto.getTransactionRef())
                .idempotencyKey(key != null ? key : "LEGACY-" + invoiceId + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID())
                .paidOn(dto.getPaidOn())
                .build();

        recordPaymentAuthoritative(payDto);

        Invoice updatedInvoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with ID: " + invoiceId));
        return toInvoiceResponseDto(updatedInvoice);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getAllPayments(Long propertyId, String tenantUid, Long invoiceId) {
        List<Payment> payments;
        if (invoiceId != null) {
            payments = paymentRepository.findByInvoiceId(invoiceId);
        } else if (tenantUid != null && !tenantUid.isBlank()) {
            payments = paymentRepository.findByTenantUid(tenantUid.trim());
        } else if (propertyId != null) {
            payments = paymentRepository.findByPropertyId(propertyId);
        } else {
            payments = paymentRepository.findAll();
        }
        return payments.stream().map(p -> {
            Invoice inv = invoiceRepository.findById(p.getInvoiceId()).orElse(null);
            return toPaymentResponseDto(p, inv);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaymentResponseDto getPaymentById(Long id) {
        Payment p = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with ID: " + id));
        Invoice inv = invoiceRepository.findById(p.getInvoiceId()).orElse(null);
        return toPaymentResponseDto(p, inv);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getPaymentsByInvoiceId(Long invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId).stream().map(p -> {
            Invoice inv = invoiceRepository.findById(p.getInvoiceId()).orElse(null);
            return toPaymentResponseDto(p, inv);
        }).collect(Collectors.toList());
    }

    // =========================================================================
    // LEDGER
    // =========================================================================

    @Transactional(readOnly = true)
    public List<LedgerTransactionResponseDto> getAllTransactions(TransactionType type) {
        return getAllTransactions(type, null);
    }

    @Transactional(readOnly = true)
    public List<LedgerTransactionResponseDto> getAllTransactions(TransactionType type, Long propertyId) {
        List<LedgerTransaction> txns;
        if (propertyId != null) {
            txns = ledgerRepository.findByPropertyIdOrderByCreatedAtDescIdDesc(propertyId);
            if (type != null) {
                txns = txns.stream().filter(t -> t.getType() == type).toList();
            }
        } else if (type != null) {
            txns = ledgerRepository.findByTypeOrderByCreatedAtDescIdDesc(type);
        } else {
            txns = ledgerRepository.findAllByOrderByCreatedAtDescIdDesc();
        }
        return txns.stream().map(this::toLedgerResponseDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LedgerTransactionResponseDto> getTransactionsByTenant(String tenantUid) {
        return ledgerRepository.findByTenantUidOrderByCreatedAtDescIdDesc(tenantUid).stream()
                .map(this::toLedgerResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LedgerTransactionResponseDto> getTransactionsByInvoice(Long invoiceId) {
        return ledgerRepository.findByInvoiceIdOrderByCreatedAtDescIdDesc(invoiceId).stream()
                .map(this::toLedgerResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public LedgerTransactionResponseDto recordCustomTransaction(LedgerTransactionRequestDto dto) {
        long currentBalance = getLatestRunningBalance(dto.getPropertyId());
        long newBalance = dto.getType() == TransactionType.CREDIT
                ? currentBalance + dto.getAmount()
                : Math.max(0, currentBalance - dto.getAmount());

        String dateStr = dto.getDate() != null && !dto.getDate().isBlank()
                ? dto.getDate().trim()
                : LocalDate.now().toString();

        String refNo = "TXN-" + (10000000000L + (long)(Math.random() * 90000000000L));

        LedgerTransaction txn = LedgerTransaction.builder()
                .referenceNumber(refNo)
                .date(dateStr)
                .type(dto.getType())
                .accountHead(dto.getAccountHead().trim())
                .description(dto.getDescription().trim())
                .tenantOrVendor(dto.getTenantOrVendor().trim())
                .amount(dto.getAmount())
                .paymentMode(dto.getPaymentMode().trim())
                .propertyId(dto.getPropertyId())
                .runningBalance(newBalance)
                .createdAt(LocalDateTime.now())
                .build();

        LedgerTransaction saved = ledgerRepository.save(txn);
        log.info("Recorded custom ledger transaction: {}. Balance: ₹{}", saved.getReferenceNumber(), newBalance);
        return toLedgerResponseDto(saved);
    }

    public long getLatestRunningBalance() {
        return ledgerRepository.findTopByOrderByCreatedAtDescIdDesc()
                .map(LedgerTransaction::getRunningBalance)
                .orElse(0L);
    }

    public long getLatestRunningBalance(Long propertyId) {
        if (propertyId == null) {
            return getLatestRunningBalance();
        }
        return ledgerRepository.findTopByPropertyIdOrderByCreatedAtDescIdDesc(propertyId)
                .map(LedgerTransaction::getRunningBalance)
                .orElse(0L);
    }

    // =========================================================================
    // HELPERS & MAPPERS
    // =========================================================================

    private String generateInvoiceNumber(String monthYear) {
        String cleanMonth = monthYear.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (cleanMonth.length() > 6) {
            cleanMonth = cleanMonth.substring(0, 6);
        }
        return "INV-" + cleanMonth + "-" + INVOICE_SEQ.getAndIncrement();
    }

    private String generatePaymentReference() {
        return "PAY-" + LocalDate.now().getYear() + "-" + PAYMENT_SEQ.getAndIncrement();
    }

    private InvoiceResponseDto toInvoiceResponseDto(Invoice invoice) {
        int paid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : 0;
        int balance = Math.max(0, invoice.getAmount() - paid);

        return InvoiceResponseDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .tenantUid(invoice.getTenantUid())
                .tenantName(invoice.getTenantName())
                .roomNo(invoice.getRoomNo())
                .propertyId(invoice.getPropertyId())
                .monthYear(invoice.getMonthYear())
                .amount(invoice.getAmount())
                .paidAmount(paid)
                .remainingBalance(balance)
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus())
                .paidOn(invoice.getPaidOn())
                .paymentMode(invoice.getPaymentMode())
                .transactionRef(invoice.getTransactionRef())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    private PaymentResponseDto toPaymentResponseDto(Payment payment, Invoice invoice) {
        int total = invoice != null && invoice.getAmount() != null ? invoice.getAmount() : payment.getAmount();
        int paid = invoice != null && invoice.getPaidAmount() != null ? invoice.getPaidAmount() : payment.getAmount();
        int remaining = Math.max(0, total - paid);
        InvoiceStatus invStatus = invoice != null ? invoice.getStatus() : InvoiceStatus.PAID;
        String invNum = invoice != null ? invoice.getInvoiceNumber() : "";
        String tName = invoice != null ? invoice.getTenantName() : "";

        return PaymentResponseDto.builder()
                .id(payment.getId())
                .paymentReference(payment.getPaymentReference())
                .invoiceId(payment.getInvoiceId())
                .invoiceNumber(invNum)
                .tenantUid(payment.getTenantUid())
                .tenantName(tName)
                .amount(payment.getAmount())
                .paymentMode(payment.getPaymentMode())
                .transactionReference(payment.getTransactionReference())
                .idempotencyKey(payment.getIdempotencyKey())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .propertyId(payment.getPropertyId())
                .invoiceTotalAmount(total)
                .invoicePaidAmount(paid)
                .invoiceRemainingBalance(remaining)
                .invoiceStatus(invStatus)
                .build();
    }

    private LedgerTransactionResponseDto toLedgerResponseDto(LedgerTransaction txn) {
        return LedgerTransactionResponseDto.builder()
                .id(txn.getId())
                .referenceNumber(txn.getReferenceNumber())
                .date(txn.getDate())
                .type(txn.getType())
                .accountHead(txn.getAccountHead())
                .description(txn.getDescription())
                .tenantOrVendor(txn.getTenantOrVendor())
                .amount(txn.getAmount())
                .paymentMode(txn.getPaymentMode())
                .propertyId(txn.getPropertyId())
                .runningBalance(txn.getRunningBalance())
                .tenantUid(txn.getTenantUid())
                .invoiceId(txn.getInvoiceId())
                .paymentId(txn.getPaymentId())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
