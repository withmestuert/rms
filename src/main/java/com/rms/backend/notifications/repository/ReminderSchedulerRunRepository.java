package com.rms.backend.notifications.repository;

import com.rms.backend.notifications.entity.ReminderSchedulerRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReminderSchedulerRunRepository extends JpaRepository<ReminderSchedulerRun, Long> {

    List<ReminderSchedulerRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
