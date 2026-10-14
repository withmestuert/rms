package com.rms.backend.notifications.controller;

import com.rms.backend.notifications.dto.*;
import com.rms.backend.notifications.entity.ReminderLog;
import com.rms.backend.notifications.entity.ReminderSchedulerRun;
import com.rms.backend.notifications.service.RentReminderScheduler;
import com.rms.backend.notifications.service.RentReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/reminders")
@RequiredArgsConstructor
@Slf4j
public class RentReminderController {

    private final RentReminderService rentReminderService;
    private final RentReminderScheduler rentReminderScheduler;

    @GetMapping("/queue")
    public ResponseEntity<List<RentReminderItemDto>> getRemindersQueue(
            @RequestParam(required = false) String category,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        log.info("Fetching rent reminder queue (category: {}, propertyId: {})", category, effectivePropId);
        List<RentReminderItemDto> items = rentReminderService.getPendingRemindersQueue(effectivePropId, category);
        return ResponseEntity.ok(items);
    }

    @PostMapping("/send")
    public ResponseEntity<SendReminderResultDto> dispatchReminders(
            @Valid @RequestBody SendReminderRequestDto dto,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        log.info("Dispatching reminders for {} invoices via channel {}", dto.getInvoiceIds().size(), dto.getChannel());
        SendReminderResultDto result = rentReminderService.dispatchReminders(dto, effectivePropId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<ReminderStatsDto> getReminderStats(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        ReminderStatsDto stats = rentReminderService.getReminderStats(effectivePropId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/logs")
    public ResponseEntity<List<ReminderLog>> getReminderLogs(
            @RequestParam(required = false) String tenantUid,
            @RequestParam(required = false) Long invoiceId,
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        List<ReminderLog> logs = rentReminderService.getReminderLogs(effectivePropId, tenantUid, invoiceId);
        return ResponseEntity.ok(logs);
    }

    // --- Scheduler Endpoints ---

    @GetMapping("/scheduler/config")
    public ResponseEntity<ReminderSchedulerConfigDto> getSchedulerConfig() {
        return ResponseEntity.ok(rentReminderScheduler.getConfigDto());
    }

    @PutMapping("/scheduler/config")
    public ResponseEntity<ReminderSchedulerConfigDto> updateSchedulerConfig(@RequestBody ReminderSchedulerConfigDto dto) {
        log.info("Updating reminder scheduler configuration");
        return ResponseEntity.ok(rentReminderScheduler.updateConfig(dto));
    }

    @PostMapping("/scheduler/trigger")
    public ResponseEntity<ReminderSchedulerRunResultDto> triggerSchedulerRun(
            @RequestHeader(value = "X-Property-Id", required = false) Long headerPropId,
            @RequestParam(value = "propertyId", required = false) Long queryPropId) {
        Long effectivePropId = queryPropId != null ? queryPropId : headerPropId;
        log.info("Manually triggering automated reminder scheduler run (propertyId: {})", effectivePropId);
        ReminderSchedulerRunResultDto result = rentReminderScheduler.executeReminderJob("MANUAL", effectivePropId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/scheduler/runs")
    public ResponseEntity<List<ReminderSchedulerRun>> getSchedulerRuns(
            @RequestParam(defaultValue = "15") int limit) {
        return ResponseEntity.ok(rentReminderScheduler.getRecentRuns(limit));
    }
}
