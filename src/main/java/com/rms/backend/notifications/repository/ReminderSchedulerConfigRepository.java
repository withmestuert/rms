package com.rms.backend.notifications.repository;

import com.rms.backend.notifications.entity.ReminderSchedulerConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderSchedulerConfigRepository extends JpaRepository<ReminderSchedulerConfig, Long> {
}
