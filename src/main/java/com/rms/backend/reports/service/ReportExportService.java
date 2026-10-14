package com.rms.backend.reports.service;

import com.rms.backend.billing.entity.LedgerTransaction;
import com.rms.backend.billing.repository.LedgerTransactionRepository;
import com.rms.backend.reports.dto.DefaulterAgingDto;
import com.rms.backend.reports.dto.SecurityDepositReportDto;
import com.rms.backend.rooms.entity.Bed;
import com.rms.backend.rooms.repository.BedRepository;
import com.rms.backend.tenants.entity.Tenant;
import com.rms.backend.tenants.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final TenantRepository tenantRepository;
    private final BedRepository bedRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final AnalyticsService analyticsService;

    @Transactional(readOnly = true)
    public byte[] exportRentRollCsv(Long propertyId) {
        List<Tenant> tenants = propertyId != null
                ? tenantRepository.findActiveByPropertyId(propertyId)
                : tenantRepository.findAllActive();

        Map<Long, Bed> bedMap = bedRepository.findAll().stream()
                .collect(Collectors.toMap(Bed::getId, b -> b, (b1, b2) -> b1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("Tenant UID,Name,Mobile Number,Aadhaar No,Tenant Type,Organization,Room No,Bed Number,Monthly Rent (INR),Advance Deposit (INR),Status");

            for (Tenant t : tenants) {
                String bedNum = (t.getBedId() != null && bedMap.containsKey(t.getBedId()))
                        ? bedMap.get(t.getBedId()).getBedNumber()
                        : "-";

                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,\"%s\"%n",
                        clean(t.getUid()),
                        clean(t.getName()),
                        clean(t.getMobileNumber()),
                        clean(t.getAadhaarNo()),
                        clean(t.getTenantType()),
                        clean(t.getOrganizationName()),
                        clean(t.getRoomNo()),
                        clean(bedNum),
                        t.getStandardRent() != null ? t.getStandardRent() : 0,
                        t.getAdvancePaid() != null ? t.getAdvancePaid() : 0,
                        clean(t.getStatus())
                );
            }
        }
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] exportDefaultersCsv(Long propertyId) {
        DefaulterAgingDto agingDto = analyticsService.getDefaultersAging(propertyId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("Tenant UID,Tenant Name,Mobile Number,Room No,Bed Number,Invoice No,Month/Year,Due Date,Total Amount (INR),Paid Amount (INR),Pending Balance (INR),Days Overdue,Aging Category");

            for (DefaulterAgingDto.DefaulterItem item : agingDto.getDefaulters()) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%d,%d,\"%s\"%n",
                        clean(item.getTenantUid()),
                        clean(item.getTenantName()),
                        clean(item.getMobileNumber()),
                        clean(item.getRoomNo()),
                        clean(item.getBedNumber()),
                        clean(item.getInvoiceNumber()),
                        clean(item.getMonthYear()),
                        clean(item.getDueDate()),
                        item.getAmount(),
                        item.getPaidAmount(),
                        item.getPendingAmount(),
                        item.getDaysOverdue(),
                        clean(item.getAgingCategory())
                );
            }
        }
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] exportDepositsCsv(Long propertyId) {
        SecurityDepositReportDto depositsDto = analyticsService.getSecurityDepositsReport(propertyId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("Tenant UID,Resident Name,Mobile Number,Room No,Bed Number,Deposit Held (INR),Payment Status,Vacate Status,Repayable Estimate (INR)");

            for (SecurityDepositReportDto.DepositItem item : depositsDto.getDeposits()) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",%.2f%n",
                        clean(item.getTenantUid()),
                        clean(item.getTenantName()),
                        clean(item.getMobileNumber()),
                        clean(item.getRoomNo()),
                        clean(item.getBedNumber()),
                        item.getDepositAmount(),
                        clean(item.getAdvanceStatus()),
                        clean(item.getVacateStatus()),
                        item.getRepayableEstimate()
                );
            }
        }
        return out.toByteArray();
    }

    @Transactional(readOnly = true)
    public byte[] exportFinancialLedgerCsv(Long propertyId) {
        List<LedgerTransaction> txs = propertyId != null
                ? ledgerTransactionRepository.findByPropertyIdOrderByCreatedAtDescIdDesc(propertyId)
                : ledgerTransactionRepository.findAllByOrderByCreatedAtDescIdDesc();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("Reference No,Date,Account Head,Description,Type,Tenant / Vendor,Payment Mode,Amount (INR),Running Balance (INR)");

            for (LedgerTransaction tx : txs) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d%n",
                        clean(tx.getReferenceNumber()),
                        clean(tx.getDate()),
                        clean(tx.getAccountHead()),
                        clean(tx.getDescription()),
                        tx.getType() != null ? tx.getType().name() : "",
                        clean(tx.getTenantOrVendor()),
                        clean(tx.getPaymentMode()),
                        tx.getAmount() != null ? tx.getAmount() : 0,
                        tx.getRunningBalance() != null ? tx.getRunningBalance() : 0
                );
            }
        }
        return out.toByteArray();
    }

    private String clean(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }
}
