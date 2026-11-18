package com.solar.Quotation.scheduler;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.repository.ReminderQueryRepository;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.NotificationPublisher;
import com.solar.Quotation.util.DiffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Expiry reminders for ONE partner. Kept apart from QuotationExpiryJob so that an exception here is a per-partner
 * failure that the job catches, logs with the partnerId, audits, and moves past.
 *
 * Tenant rules for a job (there is no signed-in user, TenantContext is empty):
 *  - every query is pinned to the partner being processed, and only joins customers of that same partner;
 *  - the partnerId handed to the notification publisher is that partner, never a value from another partner's row;
 *  - the audit rows are written under that partner, actor SYSTEM (recordAs with no actor).
 * Rows are read in keyset batches (id > lastId), nothing is held in a long transaction; each notification is saved by
 * NotificationWriter in its own transaction and its dedupe key makes a re-run harmless.
 */
@Component
public class QuotationExpiryProcessor {

    private static final Logger log = LoggerFactory.getLogger(QuotationExpiryProcessor.class);

    private final ReminderQueryRepository queries;
    private final NotificationPublisher publisher;
    private final AuditService auditService;
    private final int batchSize;

    public QuotationExpiryProcessor(ReminderQueryRepository queries, NotificationPublisher publisher,
                                    AuditService auditService,
                                    @Value("${app.notifications.expiry-batch-size:200}") int batchSize) {
        this.queries = queries;
        this.publisher = publisher;
        this.auditService = auditService;
        this.batchSize = Math.max(batchSize, 1);
    }

    /** What happened for one partner in one run. */
    public record PartnerResult(Long partnerId, int processed, int failed) { }

    /**
     * @throws RuntimeException when a whole batch cannot be read (database problem). Row level problems do not
     *                          throw: they are counted in {@code failed} and the next row is tried.
     */
    public PartnerResult processPartner(Long partnerId, LocalDate today, LocalDate until) {
        MDC.put("partnerId", String.valueOf(partnerId));
        try {
            int processed = 0;
            int failed = 0;
            long lastId = 0L;
            List<Object[]> rows;
            do {
                rows = queries.expiringQuotationsForPartner(partnerId, QuotationStatus.PENDING, today, until, lastId,
                        PageRequest.of(0, batchSize));
                for (Object[] r : rows) {
                    long quotationId = ((Number) r[0]).longValue();
                    lastId = Math.max(lastId, quotationId);
                    try {
                        Long rowPartner = ((Number) r[1]).longValue();
                        if (!partnerId.equals(rowPartner)) {           // cannot happen (query is pinned); never cross tenants
                            throw new IllegalStateException("quotation " + quotationId + " belongs to partner " + rowPartner);
                        }
                        publisher.quotationExpiring(
                                quotationId,
                                partnerId,
                                (String) r[2],
                                r[3] == null ? null : ((Number) r[3]).longValue(),
                                (String) r[5],
                                (LocalDate) r[4],
                                today);
                        processed++;
                    } catch (RuntimeException ex) {
                        failed++;
                        log.warn("[QUOTATION-JOB] reminder failed partnerId={} quotationId={}", partnerId, quotationId, ex);
                    }
                }
            } while (rows.size() == batchSize);

            if (processed > 0 || failed > 0) {
                writeAudit(partnerId, today, "COMPLETED", processed, failed, null);
            }
            return new PartnerResult(partnerId, processed, failed);
        } finally {
            MDC.remove("partnerId");
        }
    }

    /** Called by the job when processPartner threw, so the failed partner leaves a trace in ITS audit log. */
    public void auditPartnerFailure(Long partnerId, LocalDate today, RuntimeException ex) {
        writeAudit(partnerId, today, "FAILED", 0, 0, ex.getClass().getSimpleName());
    }

    /** One summary row per partner per run, actor SYSTEM. No quotation data changed, so nothing finer to audit. */
    private void writeAudit(Long partnerId, LocalDate today, String outcome, int processed, int failed, String error) {
        try {
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("outcome", outcome);
            after.put("remindersRaised", processed);
            after.put("remindersFailed", failed);
            after.put("runDate", today.toString());
            if (error != null) after.put("error", error);          // exception class only, never its message
            auditService.recordEvent(null, AuditAction.CREATE, "QUOTATION", "QuotationExpiryRun", null,
                    today.toString(), partnerId, null, after);
        } catch (RuntimeException ex) {
            // an audit problem must not undo or hide the reminders that were already raised
            log.warn("[QUOTATION-JOB] audit row failed partnerId={}", partnerId, ex);
        }
    }
}