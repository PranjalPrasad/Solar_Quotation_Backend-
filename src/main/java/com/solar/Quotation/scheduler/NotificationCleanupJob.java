package com.solar.Quotation.scheduler;
import com.solar.Quotation.repository.NotificationCleanupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Every night: hard-deletes notifications older than the retention period, PARTNER BY PARTNER (PLATFORM included),
 * in batches (see NotificationCleanupProcessor). A failure in one partner is logged with its partnerId and the job
 * carries on with the next partner.
 *
 * Log lines (all start with [NOTIFY]):
 *   INFO  started: cutoff and number of partners
 *   INFO  per partner: removed rows and batches (and "capped" when the per-run limit stopped it)
 *   ERROR per failed partner: partnerId + stack
 *   INFO  finished: partners ok / failed, rows removed, duration in ms
 */
@Component
public class NotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJob.class);

    private final NotificationCleanupRepository repository;
    private final NotificationCleanupProcessor processor;

    @Value("${app.notifications.retention-days:90}")
    private int retentionDays;

    public NotificationCleanupJob(NotificationCleanupRepository repository, NotificationCleanupProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    @Scheduled(cron = "${app.notifications.cleanup-cron:0 30 2 * * *}", zone = "Asia/Kolkata")
    public void run() {
        runFor(LocalDateTime.now(ZoneOffset.UTC).minusDays(Math.max(retentionDays, 7)));
    }

    /** The whole run for one cutoff (UTC). Public so a test (or an admin trigger later) can call it. */
    public RunSummary runFor(LocalDateTime cutoff) {
        long started = System.nanoTime();
        MDC.put("requestId", "notify-cleanup-" + UUID.randomUUID().toString().substring(0, 8));
        int partnersOk = 0;
        int partnersFailed = 0;
        int removed = 0;
        try {
            List<Long> partnerIds;
            try {
                partnerIds = repository.partnerIdsWithRowsOlderThan(cutoff);
            } catch (RuntimeException ex) {
                log.error("[NOTIFY] cleanup could not list partners, run aborted cutoff={}", cutoff, ex);
                return new RunSummary(0, 1, 0, elapsedMs(started));
            }
            log.info("[NOTIFY] cleanup started cutoff={} partners={}", cutoff, partnerIds.size());

            for (Long partnerId : partnerIds) {
                try {
                    NotificationCleanupProcessor.PartnerResult r = processor.purgePartner(partnerId, cutoff);
                    removed += r.removed();
                    log.info("[NOTIFY] cleanup partnerId={} removed={} batches={}{}", partnerId, r.removed(), r.batches(),
                            r.capped() ? " capped (more old rows remain, next run continues)" : "");
                    partnersOk++;
                } catch (RuntimeException ex) {
                    partnersFailed++;
                    log.error("[NOTIFY] cleanup failed partnerId={}, continuing with the next partner", partnerId, ex);
                }
            }
            long ms = elapsedMs(started);
            log.info("[NOTIFY] cleanup finished partnersOk={} partnersFailed={} removed={} durationMs={}",
                    partnersOk, partnersFailed, removed, ms);
            return new RunSummary(partnersOk, partnersFailed, removed, ms);
        } finally {
            MDC.remove("requestId");
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public record RunSummary(int partnersOk, int partnersFailed, int removed, long durationMs) { }
}
