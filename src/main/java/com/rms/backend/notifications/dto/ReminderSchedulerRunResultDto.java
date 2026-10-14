package com.rms.backend.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderSchedulerRunResultDto {

    private Long runId;
    private String triggeredBy;
    private String status;
    private Integer invoicesEvaluated;
    private Integer remindersDispatched;
    private Integer remindersSkipped;
    private List<String> dispatchedInvoiceNumbers;
    private List<String> skippedInvoiceNumbers;
    private String summaryNotes;
    private Long executionDurationMs;
    private LocalDateTime executedAt;
}
