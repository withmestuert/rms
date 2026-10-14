package com.rms.backend.billing.service;

import com.rms.backend.billing.dto.CycleGenerationRequestDto;
import com.rms.backend.billing.dto.CycleGenerationResultDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automated Monthly Rent Invoice Scheduler.
 * Automatically generates monthly rent cycle invoices for all active residents
 * on the 1st of every month (or via configured cron schedule).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoRentInvoiceScheduler {

    private final BillingService billingService;

    @Value("${rms.billing.auto-invoice.enabled:true}")
    private boolean enabled;

    @Value("${rms.billing.auto-invoice.due-day:5}")
    private int dueDay;

    @Getter
    @Setter
    public static class AutoCycleRunStatus {
        private LocalDateTime lastRunAt;
        private String monthYear;
        private String dueDate;
        private int generatedCount;
        private int skippedCount;
        private long totalAmount;
        private String status;
        private String error;
    }

    private final AutoCycleRunStatus lastRunStatus = new AutoCycleRunStatus();

    /**
     * Automated cron schedule: Default runs at 00:05 AM on the 1st day of every month.
     */
    @Scheduled(cron = "${rms.billing.auto-invoice.cron:0 5 0 1 * ?}")
    public void runMonthlyAutoInvoiceGeneration() {
        if (!enabled) {
            log.info("[AUTO INVOICE CRON] Auto monthly rent generation is disabled in configuration. Skipping.");
            return;
        }

        log.info("[AUTO INVOICE CRON] Firing automated monthly rent invoice generation at {}", LocalDateTime.now());
        LocalDate now = LocalDate.now();
        executeAutoCycle(now, dueDay, null);
    }

    /**
     * Executes the monthly auto-cycle generation for the given reference date and optional propertyId.
     */
    public CycleGenerationResultDto executeAutoCycle(LocalDate targetDate, Integer targetDueDay, Long propertyId) {
        LocalDate refDate = targetDate != null ? targetDate : LocalDate.now();
        int day = targetDueDay != null ? targetDueDay : this.dueDay;

        // E.g. "October 2026"
        String monthYear = refDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));

        // Compute due date (e.g. 5th of that month, clamped if month has fewer days)
        int maxDay = refDate.lengthOfMonth();
        int effectiveDueDay = Math.min(day, maxDay);
        LocalDate dueLocalDate = refDate.withDayOfMonth(effectiveDueDay);
        String dueDate = dueLocalDate.toString(); // "YYYY-MM-DD"

        log.info("[AUTO INVOICE] Executing cycle generation for Month: {}, Due Date: {}, Property: {}",
                monthYear, dueDate, propertyId != null ? propertyId : "ALL");

        // Ensure database partition exists and upcoming partitions are provisioned
        billingService.ensureMonthlyPartition(refDate);
        billingService.autoProvisionPartitions(3);

        CycleGenerationRequestDto requestDto = CycleGenerationRequestDto.builder()
                .monthYear(monthYear)
                .dueDate(dueDate)
                .propertyId(propertyId)
                .build();

        try {
            CycleGenerationResultDto result = billingService.generateCycleInvoices(requestDto);
            
            synchronized (lastRunStatus) {
                lastRunStatus.setLastRunAt(LocalDateTime.now());
                lastRunStatus.setMonthYear(monthYear);
                lastRunStatus.setDueDate(dueDate);
                lastRunStatus.setGeneratedCount(result.getGeneratedCount());
                lastRunStatus.setSkippedCount(result.getSkippedCount());
                lastRunStatus.setTotalAmount(result.getTotalAmount());
                lastRunStatus.setStatus("SUCCESS");
                lastRunStatus.setError(null);
            }

            log.info("[AUTO INVOICE] Cycle generation completed successfully: {} generated, {} skipped, ₹{} total",
                    result.getGeneratedCount(), result.getSkippedCount(), result.getTotalAmount());

            return result;
        } catch (Exception e) {
            log.error("[AUTO INVOICE] Failed executing auto rent generation for month {}", monthYear, e);
            synchronized (lastRunStatus) {
                lastRunStatus.setLastRunAt(LocalDateTime.now());
                lastRunStatus.setMonthYear(monthYear);
                lastRunStatus.setDueDate(dueDate);
                lastRunStatus.setStatus("FAILED");
                lastRunStatus.setError(e.getMessage());
            }
            throw e;
        }
    }

    public Map<String, Object> getSchedulerStatus() {
        Map<String, Object> map = new ConcurrentHashMap<>();
        map.put("enabled", enabled);
        map.put("defaultDueDay", dueDay);
        synchronized (lastRunStatus) {
            map.put("lastRunAt", lastRunStatus.getLastRunAt() != null ? lastRunStatus.getLastRunAt().toString() : "NEVER");
            map.put("lastMonthYear", lastRunStatus.getMonthYear() != null ? lastRunStatus.getMonthYear() : "-");
            map.put("lastDueDate", lastRunStatus.getDueDate() != null ? lastRunStatus.getDueDate() : "-");
            map.put("lastGeneratedCount", lastRunStatus.getGeneratedCount());
            map.put("lastSkippedCount", lastRunStatus.getSkippedCount());
            map.put("lastTotalAmount", lastRunStatus.getTotalAmount());
            map.put("lastStatus", lastRunStatus.getStatus() != null ? lastRunStatus.getStatus() : "IDLE");
            if (lastRunStatus.getError() != null) {
                map.put("lastError", lastRunStatus.getError());
            }
        }
        return map;
    }
}
