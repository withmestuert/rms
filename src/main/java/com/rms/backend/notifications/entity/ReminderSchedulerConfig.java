package com.rms.backend.notifications.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_scheduler_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderSchedulerConfig {

    @Id
    private Long id; // Singleton row with id=1

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "cron_expression", nullable = false, length = 50)
    private String cronExpression;

    @Column(name = "morning_time", nullable = false, length = 20)
    private String morningTime;

    @Column(name = "target_categories", nullable = false, length = 250)
    private String targetCategories;

    @Column(nullable = false, length = 50)
    private String channel;

    @Column(name = "cooldown_hours", nullable = false)
    private Integer cooldownHours;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "last_run_status", length = 50)
    private String lastRunStatus;

    @Column(name = "last_run_summary", length = 500)
    private String lastRunSummary;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        if (id == null) {
            id = 1L;
        }
        updatedAt = LocalDateTime.now();
    }
}
