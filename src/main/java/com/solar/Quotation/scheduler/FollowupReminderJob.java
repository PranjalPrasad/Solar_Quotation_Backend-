package com.solar.Quotation.scheduler;

import com.solar.Quotation.Enum.FollowupStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.repository.ReminderQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Every morning: pending follow-ups that are due today or overdue, processed PARTNER BY PARTNER.
 * A failure in one partner is logged with its partnerId and the job carries on with the next partner.
 *
 * Log lines (all start with [FOLLOWUP-JOB]):
 *   INFO  per partner: processed count (and failed count when not 0)
 *   ERROR per failed partner: partnerId + stack
 *   INFO  run summary: partners ok / failed, reminders processed, duration in ms
 */
@Component
public class FollowupReminderJob {

    private static final Logger log = LoggerFactory.getLogger(FollowupReminderJob.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ReminderQueryRepository queries;
    private final FollowupReminderProcessor processor;

    public FollowupReminderJob(ReminderQueryRepository queries, FollowupReminderProcessor processor) {
        this.queries = queries;
        this.processor = processor;
    }

    @Scheduled(cron = "${app.notifications.followup-cron:0 0 9 * * *}", zone = "Asia/Kolkata")
    public void run() {
        runFor(LocalDate.now(IST));
    }

    /** The whole run for one business day. Public so a test (or an admin trigger later) can call it. */
    public RunSummary runFor(LocalDate today) {
        long started = System.nanoTime();
        MDC.put("requestId", "followup-job-" + UUID.randomUUID().toString().substring(0, 8));
        int partnersOk = 0;
        int partnersFailed = 0;
        int processed = 0;
        try {
            List<Long> partnerIds;
            try {
                partnerIds = queries.partnersWithDueFollowups(FollowupStatus.PENDING, RecordStatus.ACTIVE, today);
            } catch (RuntimeException ex) {
                log.error("[FOLLOWUP-JOB] could not list partners with due follow-ups, run aborted", ex);
                return new RunSummary(0, 1, 0, elapsedMs(started));
            }
            log.info("[FOLLOWUP-JOB] started date={} partners={}", today, partnerIds.size());

            for (Long partnerId : partnerIds) {
                try {
                    FollowupReminderProcessor.PartnerResult r = processor.processPartner(partnerId, today);
                    processed += r.processed();
                    if (r.failed() > 0) {
                        log.info("[FOLLOWUP-JOB] partnerId={} processed={} failed={}", partnerId, r.processed(), r.failed());
                    } else {
                        log.info("[FOLLOWUP-JOB] partnerId={} processed={}", partnerId, r.processed());
                    }
                    partnersOk++;
                } catch (RuntimeException ex) {
                    partnersFailed++;
                    log.error("[FOLLOWUP-JOB] partner failed partnerId={}, continuing with the next partner", partnerId, ex);
                }
            }
            long ms = elapsedMs(started);
            log.info("[FOLLOWUP-JOB] finished partnersOk={} partnersFailed={} processed={} durationMs={}",
                    partnersOk, partnersFailed, processed, ms);
            return new RunSummary(partnersOk, partnersFailed, processed, ms);
        } finally {
            MDC.remove("requestId");
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public record RunSummary(int partnersOk, int partnersFailed, int processed, long durationMs) { }
}