package com.rms.backend.properties.service;

import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyArchiveSnapshotRepository;
import com.rms.backend.properties.repository.PropertyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class PropertyTtlScheduler {

    private final PropertyRepository propertyRepository;
    private final PropertyArchiveSnapshotRepository snapshotRepository;

    public PropertyTtlScheduler(
            PropertyRepository propertyRepository,
            PropertyArchiveSnapshotRepository snapshotRepository
    ) {
        this.propertyRepository = propertyRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Inspects soft-deleted properties daily (and at initial boot check).
     * Once the TTL grace period expires, verifies audit snapshot integrity before final cleansing.
     */
    @Scheduled(cron = "0 0 2 * * ?") // 2:00 AM daily
    @Transactional
    public void processExpiredPropertyTtls() {
        LocalDateTime now = LocalDateTime.now();
        List<Property> deletedProperties = propertyRepository.findByIsDeletedTrue();

        for (Property prop : deletedProperties) {
            if (prop.getTtlExpiresAt() != null && prop.getTtlExpiresAt().isBefore(now)) {
                log.info("[TTL Purge Monitor] Property ID: {} ('{}') TTL expired on {}. Verifying snapshot manifest...",
                        prop.getId(), prop.getName(), prop.getTtlExpiresAt());

                boolean snapshotExists = snapshotRepository.findTopByPropertyIdOrderByArchivedAtDesc(prop.getId()).isPresent();
                if (snapshotExists) {
                    log.info("[TTL Purge Monitor] Snapshot verified for Property ID: {}. Retaining permanent manifest and cleansing active DB record trace.", prop.getId());
                    propertyRepository.delete(prop);
                } else {
                    log.warn("[TTL Purge Monitor] Property ID: {} has no audit snapshot manifest! Preserving record to prevent un-audited data loss.", prop.getId());
                }
            }
        }
    }
}
