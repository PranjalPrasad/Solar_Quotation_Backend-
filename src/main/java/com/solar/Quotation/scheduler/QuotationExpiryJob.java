package com.solar.Quotation.scheduler;

import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.repository.ReminderQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Every morning: PENDING quotations whose validity ends within the next few days, processed PARTNER BY PARTNER, in
 * batches. A failure in one partner is logged with its partnerId, audited under that partner as SYSTEM, and the job
 * carries on with the next partner.
 *
 * Log lines (all start with [QUOTATION-JOB]):
 *   INFO  started: date and number of partners
 *   INFO  per partner: processed count (and failed count when not 0)
 *   ERROR per failed partner: partnerId + stack
 *   INFO  finished: partners ok / failed, reminders processed, reminders failed, duration in ms
 */
@Component
public class QuotationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(QuotationExpiryJob.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ReminderQueryRepository queries;
    private final QuotationExpiryProcessor processor;

    @Value("${app.notifications.expiry-warning-days:3}")
    private int warningDays;

    public QuotationExpiryJob(ReminderQueryRepository queries, QuotationExpiryProcessor processor) {
        this.queries = queries;
        this.processor = processor;
    }

    @Scheduled(cron = "${app.notifications.expiry-cron:0 15 9 * * *}", zone = "Asia/Kolkata")
    public void run() {
        runFor(LocalDate.now(IST));
    }

    /** The whole run for one business day. Public so a test (or an admin trigger later) can call it. */
    public RunSummary runFor(LocalDate today) {
        long started = System.nanoTime();
        MDC.put("requestId", "quotation-job-" + UUID.randomUUID().toString().substring(0, 8));
        LocalDate until = today.plusDays(Math.max(warningDays, 0));
        int partnersOk = 0;
        int partnersFailed = 0;
        int processed = 0;
        int reminderFailures = 0;
        try {
            List<Long> partnerIds;
            try {
                partnerIds = queries.partnersWithExpiringQuotations(QuotationStatus.PENDING, RecordStatus.ACTIVE, today, until);
            } catch (RuntimeException ex) {
                log.error("[QUOTATION-JOB] could not list partners with expiring quotations, run aborted", ex);
                return new RunSummary(0, 1, 0, 0, elapsedMs(started));
            }
            log.info("[QUOTATION-JOB] started date={} until={} partners={}", today, until, partnerIds.size());

            for (Long partnerId : partnerIds) {
                try {
                    QuotationExpiryProcessor.PartnerResult r = processor.processPartner(partnerId, today, until);
                    processed += r.processed();
                    reminderFailures += r.failed();
                    if (r.failed() > 0) {
                        log.info("[QUOTATION-JOB] partnerId={} processed={} failed={}", partnerId, r.processed(), r.failed());
                    } else {
                        log.info("[QUOTATION-JOB] partnerId={} processed={}", partnerId, r.processed());
                    }
                    partnersOk++;
                } catch (RuntimeException ex) {
                    partnersFailed++;
                    log.error("[QUOTATION-JOB] partner failed partnerId={}, continuing with the next partner", partnerId, ex);
                    processor.auditPartnerFailure(partnerId, today, ex);
                }
            }
            long ms = elapsedMs(started);
            log.info("[QUOTATION-JOB] finished partnersOk={} partnersFailed={} processed={} reminderFailures={} durationMs={}",
                    partnersOk, partnersFailed, processed, reminderFailures, ms);
            return new RunSummary(partnersOk, partnersFailed, processed, reminderFailures, ms);
        } finally {
            MDC.remove("requestId");
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    public record RunSummary(int partnersOk, int partnersFailed, int processed, int reminderFailures, long durationMs) { }
}