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
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final LedgerTransactionRepository ledgerRepository;
    private final TenantRepository tenantRepository;

    private static final AtomicLong INVOICE_SEQ = new AtomicLong(1001);

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
        List<Tenant> tenants = tenantRepository.findAll();
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

    @Transactional
    public InvoiceResponseDto recordPayment(Long invoiceId, RecordPaymentDto dto) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with ID: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new DuplicateResourceException("Invoice " + invoice.getInvoiceNumber() + " is already marked as PAID");
        }

        String paidDate = dto.getPaidOn() != null && !dto.getPaidOn().isBlank()
                ? dto.getPaidOn().trim()
                : LocalDate.now().toString();

        String txnRef = dto.getTransactionRef() != null && !dto.getTransactionRef().isBlank()
                ? dto.getTransactionRef().trim()
                : "TXN-" + System.currentTimeMillis();

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidOn(paidDate);
        invoice.setPaymentMode(dto.getPaymentMode());
        invoice.setTransactionRef(txnRef);

        Invoice updatedInvoice = invoiceRepository.save(invoice);

        // Record in ledger
        long currentBalance = getLatestRunningBalance();
        long newBalance = currentBalance + invoice.getAmount();

        LedgerTransaction ledgerTxn = LedgerTransaction.builder()
                .referenceNumber(txnRef)
                .date(paidDate)
                .type(TransactionType.CREDIT)
                .accountHead("Rent Payment")
                .description("Monthly Rent - Room " + invoice.getRoomNo() + " (" + invoice.getTenantName() + ")")
                .tenantOrVendor(invoice.getTenantName())
                .amount(invoice.getAmount())
                .paymentMode(dto.getPaymentMode())
                .propertyId(invoice.getPropertyId())
                .runningBalance(newBalance)
                .createdAt(LocalDateTime.now())
                .build();

        ledgerRepository.save(ledgerTxn);
        log.info("Recorded payment for invoice {}. New ledger balance: ₹{}", invoice.getInvoiceNumber(), newBalance);

        return toInvoiceResponseDto(updatedInvoice);
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

    @Transactional
    public LedgerTransactionResponseDto recordCustomTransaction(LedgerTransactionRequestDto dto) {
        long currentBalance = getLatestRunningBalance();
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

    private InvoiceResponseDto toInvoiceResponseDto(Invoice invoice) {
        return InvoiceResponseDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .tenantUid(invoice.getTenantUid())
                .tenantName(invoice.getTenantName())
                .roomNo(invoice.getRoomNo())
                .propertyId(invoice.getPropertyId())
                .monthYear(invoice.getMonthYear())
                .amount(invoice.getAmount())
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus())
                .paidOn(invoice.getPaidOn())
                .paymentMode(invoice.getPaymentMode())
                .transactionRef(invoice.getTransactionRef())
                .createdAt(invoice.getCreatedAt())
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
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
