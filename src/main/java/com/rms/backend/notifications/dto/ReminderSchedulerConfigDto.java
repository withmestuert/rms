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
public class ReminderSchedulerConfigDto {

    private Long id;
    private Boolean enabled;
    private String cronExpression;
    private String morningTime;
    private List<String> targetCategories;
    private String channel;
    private Integer cooldownHours;
    private LocalDateTime lastRunAt;
    private String lastRunStatus;
    private String lastRunSummary;
    private String nextRunEstimate;
}
