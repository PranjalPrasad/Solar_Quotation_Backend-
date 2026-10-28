package com.solar.Quotation.scheduler;
import com.solar.Quotation.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class NotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private final NotificationRepository repository;

    @Value("${app.notifications.retention-days:90}")
    private int retentionDays;

    public NotificationCleanupJob(NotificationRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${app.notifications.cleanup-cron:0 30 2 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void run() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(Math.max(retentionDays, 7));
        int removed = repository.purgeOlderThan(cutoff);
        log.info("Notification cleanup removed {} row(s) older than {}", removed, cutoff);
    }
}
