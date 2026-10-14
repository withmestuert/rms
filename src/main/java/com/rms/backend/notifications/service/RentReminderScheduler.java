package com.rms.backend.notifications.service;

import com.rms.backend.notifications.dto.ReminderSchedulerConfigDto;
import com.rms.backend.notifications.dto.ReminderSchedulerRunResultDto;
import com.rms.backend.notifications.dto.RentReminderItemDto;
import com.rms.backend.notifications.dto.SendReminderRequestDto;
import com.rms.backend.notifications.dto.SendReminderResultDto;
import com.rms.backend.notifications.entity.ReminderSchedulerConfig;
import com.rms.backend.notifications.entity.ReminderSchedulerRun;
import com.rms.backend.notifications.repository.ReminderLogRepository;
import com.rms.backend.notifications.repository.ReminderSchedulerConfigRepository;
import com.rms.backend.notifications.repository.ReminderSchedulerRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RentReminderScheduler {

    private final ReminderSchedulerConfigRepository configRepository;
    private final ReminderSchedulerRunRepository runRepository;
    private final ReminderLogRepository reminderLogRepository;
    private final RentReminderService rentReminderService;

    private static final String DEFAULT_CATEGORIES = "DUE_TODAY,OVERDUE_URGENT,FINAL_NOTICE";
    private static final String DEFAULT_CRON = "0 0 10 * * ?";
    private static final String DEFAULT_TIME = "10:00";

    /**
     * Morning Automated Cron Trigger.
     * Fires daily according to the configured cron (defaults to 10:00 AM daily).
     */
    @Scheduled(cron = "${rms.reminders.scheduler.cron:0 0 10 * * ?}")
    public void runMorningRemindersScheduled() {
        log.info("[CRON SCHEDULER] Triggered morning rent collection dispatch routine at {}", LocalDateTime.now());
        ReminderSchedulerConfig config = getOrCreateConfig();
        if (Boolean.FALSE.equals(config.getEnabled())) {
            log.info("[CRON SCHEDULER] Automated Morning Reminders is PAUSED in configuration. Skipping execution.");
            return;
        }

        try {
            ReminderSchedulerRunResultDto result = executeReminderJob("CRON", null);
            log.info("[CRON SCHEDULER] Completed morning dispatch: Evaluated {}, Sent {}, Skipped {}. Summary: {}",
                    result.getInvoicesEvaluated(), result.getRemindersDispatched(), result.getRemindersSkipped(), result.getSummaryNotes());
        } catch (Exception e) {
            log.error("[CRON SCHEDULER] Error during scheduled reminder execution", e);
        }
    }

    /**
     * Executes the reminder job (either via Scheduled CRON or on-demand MANUAL trigger).
     */
    @Transactional
    public ReminderSchedulerRunResultDto executeReminderJob(String triggeredBy, Long propertyId) {
        long startTimeMs = System.currentTimeMillis();
        LocalDateTime runStartTime = LocalDateTime.now();
        ReminderSchedulerConfig config = getOrCreateConfig();

        // Parse target categories
        Set<String> parsedCategories = Arrays.stream(config.getTargetCategories().split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        final Set<String> targetCategorySet = parsedCategories.isEmpty()
                ? new HashSet<>(Arrays.asList("DUE_TODAY", "OVERDUE_URGENT", "FINAL_NOTICE"))
                : parsedCategories;

        // Fetch candidate invoices across queue
        List<RentReminderItemDto> allPending = rentReminderService.getPendingRemindersQueue(propertyId, "ALL");

        List<RentReminderItemDto> matchingCandidates = allPending.stream()
                .filter(item -> targetCategorySet.contains(item.getReminderType().toUpperCase()))
                .toList();

        int cooldownHours = config.getCooldownHours() != null ? config.getCooldownHours() : 24;
        LocalDateTime cooldownCutoff = runStartTime.minusHours(cooldownHours);

        List<Long> invoiceIdsToDispatch = new ArrayList<>();
        List<String> dispatchedInvoiceNumbers = new ArrayList<>();
        List<String> skippedInvoiceNumbers = new ArrayList<>();

        for (RentReminderItemDto item : matchingCandidates) {
            boolean recentlySent = reminderLogRepository.existsByInvoiceIdAndSentAtAfter(item.getInvoiceId(), cooldownCutoff);
            if (recentlySent) {
                skippedInvoiceNumbers.add(item.getInvoiceNumber());
            } else {
                invoiceIdsToDispatch.add(item.getInvoiceId());
                dispatchedInvoiceNumbers.add(item.getInvoiceNumber());
            }
        }

        int successDispatched = 0;
        if (!invoiceIdsToDispatch.isEmpty()) {
            SendReminderRequestDto dispatchDto = SendReminderRequestDto.builder()
                    .invoiceIds(invoiceIdsToDispatch)
                    .channel(config.getChannel() != null ? config.getChannel() : "WHATSAPP")
                    .build();

            SendReminderResultDto dispatchResult = rentReminderService.dispatchReminders(dispatchDto, propertyId);
            successDispatched = dispatchResult.getSuccessCount();
        }

        long durationMs = System.currentTimeMillis() - startTimeMs;
        LocalDateTime runEndTime = LocalDateTime.now();

        String summary = String.format("Evaluated %d target invoices: %d dispatched via %s, %d skipped due to %dh cooldown.",
                matchingCandidates.size(), successDispatched, config.getChannel(), skippedInvoiceNumbers.size(), cooldownHours);

        // Record execution run log
        ReminderSchedulerRun run = ReminderSchedulerRun.builder()
                .triggeredBy(triggeredBy != null ? triggeredBy : "MANUAL")
                .startTime(runStartTime)
                .endTime(runEndTime)
                .status("SUCCESS")
                .invoicesEvaluated(matchingCandidates.size())
                .remindersDispatched(successDispatched)
                .remindersSkipped(skippedInvoiceNumbers.size())
                .summaryNotes(summary)
                .createdAt(runStartTime)
                .build();
        run = runRepository.save(run);

        // Update config status
        config.setLastRunAt(runStartTime);
        config.setLastRunStatus("SUCCESS");
        config.setLastRunSummary(summary);
        config.setUpdatedAt(LocalDateTime.now());
        configRepository.save(config);

        return ReminderSchedulerRunResultDto.builder()
                .runId(run.getId())
                .triggeredBy(run.getTriggeredBy())
                .status("SUCCESS")
                .invoicesEvaluated(matchingCandidates.size())
                .remindersDispatched(successDispatched)
                .remindersSkipped(skippedInvoiceNumbers.size())
                .dispatchedInvoiceNumbers(dispatchedInvoiceNumbers)
                .skippedInvoiceNumbers(skippedInvoiceNumbers)
                .summaryNotes(summary)
                .executionDurationMs(durationMs)
                .executedAt(runStartTime)
                .build();
    }

    /**
     * Get or seed the singleton scheduler configuration.
     */
    @Transactional
    public ReminderSchedulerConfig getOrCreateConfig() {
        return configRepository.findById(1L).orElseGet(() -> {
            ReminderSchedulerConfig fresh = ReminderSchedulerConfig.builder()
                    .id(1L)
                    .enabled(true)
                    .cronExpression(DEFAULT_CRON)
                    .morningTime(DEFAULT_TIME)
                    .targetCategories(DEFAULT_CATEGORIES)
                    .channel("WHATSAPP")
                    .cooldownHours(24)
                    .lastRunStatus("IDLE")
                    .lastRunSummary("Scheduler ready for morning dispatch.")
                    .updatedAt(LocalDateTime.now())
                    .build();
            return configRepository.save(fresh);
        });
    }

    /**
     * Returns DTO representation with formatted categories and next run calculation.
     */
    @Transactional(readOnly = true)
    public ReminderSchedulerConfigDto getConfigDto() {
        ReminderSchedulerConfig config = getOrCreateConfig();
        List<String> categories = Arrays.stream(config.getTargetCategories().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        String nextEstimate = calculateNextRun(config.getMorningTime(), config.getEnabled());

        return ReminderSchedulerConfigDto.builder()
                .id(config.getId())
                .enabled(config.getEnabled())
                .cronExpression(config.getCronExpression())
                .morningTime(config.getMorningTime())
                .targetCategories(categories)
                .channel(config.getChannel())
                .cooldownHours(config.getCooldownHours())
                .lastRunAt(config.getLastRunAt())
                .lastRunStatus(config.getLastRunStatus())
                .lastRunSummary(config.getLastRunSummary())
                .nextRunEstimate(nextEstimate)
                .build();
    }

    /**
     * Updates scheduler configuration settings.
     */
    @Transactional
    public ReminderSchedulerConfigDto updateConfig(ReminderSchedulerConfigDto dto) {
        ReminderSchedulerConfig config = getOrCreateConfig();

        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        if (dto.getMorningTime() != null && !dto.getMorningTime().isBlank()) {
            config.setMorningTime(dto.getMorningTime().trim());
            config.setCronExpression(cronFromTime(dto.getMorningTime().trim()));
        }
        if (dto.getTargetCategories() != null && !dto.getTargetCategories().isEmpty()) {
            config.setTargetCategories(String.join(",", dto.getTargetCategories()));
        }
        if (dto.getChannel() != null && !dto.getChannel().isBlank()) {
            config.setChannel(dto.getChannel().trim().toUpperCase());
        }
        if (dto.getCooldownHours() != null && dto.getCooldownHours() >= 1) {
            config.setCooldownHours(dto.getCooldownHours());
        }

        config.setUpdatedAt(LocalDateTime.now());
        config = configRepository.save(config);
        log.info("Updated reminder scheduler config: enabled={}, morningTime={}, categories={}, cooldown={}h",
                config.getEnabled(), config.getMorningTime(), config.getTargetCategories(), config.getCooldownHours());

        return getConfigDto();
    }

    /**
     * Get recent scheduler execution runs.
     */
    @Transactional(readOnly = true)
    public List<ReminderSchedulerRun> getRecentRuns(int limit) {
        return runRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(limit, 50)));
    }

    private String cronFromTime(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return String.format("0 %d %d * * ?", minute, hour);
        } catch (Exception e) {
            return DEFAULT_CRON;
        }
    }

    private String calculateNextRun(String morningTime, Boolean enabled) {
        if (Boolean.FALSE.equals(enabled)) {
            return "Scheduler is paused";
        }
        try {
            String[] parts = morningTime.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            LocalTime targetTime = LocalTime.of(hour, minute);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime scheduledToday = LocalDateTime.of(LocalDate.now(), targetTime);

            LocalDateTime next = now.isBefore(scheduledToday) ? scheduledToday : scheduledToday.plusDays(1);
            return next.toString();
        } catch (Exception e) {
            return "Tomorrow at 10:00 AM";
        }
    }
}
