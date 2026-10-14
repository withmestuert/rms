package com.rms.backend.notifications.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_scheduler_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderSchedulerRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "triggered_by", nullable = false, length = 50)
    private String triggeredBy; // "CRON", "MANUAL"

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(nullable = false, length = 50)
    private String status; // "RUNNING", "SUCCESS", "FAILED"

    @Column(name = "invoices_evaluated", nullable = false)
    private Integer invoicesEvaluated;

    @Column(name = "reminders_dispatched", nullable = false)
    private Integer remindersDispatched;

    @Column(name = "reminders_skipped", nullable = false)
    private Integer remindersSkipped;

    @Column(name = "summary_notes", columnDefinition = "TEXT")
    private String summaryNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (invoicesEvaluated == null) invoicesEvaluated = 0;
        if (remindersDispatched == null) remindersDispatched = 0;
        if (remindersSkipped == null) remindersSkipped = 0;
    }
}
