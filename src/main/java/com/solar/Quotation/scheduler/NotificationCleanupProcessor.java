package com.solar.Quotation.scheduler;

import com.solar.Quotation.repository.NotificationCleanupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Removes old notifications of ONE partner (tenant) in small batches. Kept apart from NotificationCleanupJob so that
 * an exception here is a per-partner failure that the job catches, logs with the partnerId and moves past.
 *
 * Every batch is its own short transaction (select a page of ids, delete exactly those ids, commit), so a large
 * backlog never holds a long lock and a failure part-way keeps the batches that already committed.
 * Every statement carries the partnerId, so a batch can never touch another tenant's rows.
 */
@Component
public class NotificationCleanupProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupProcessor.class);

    /** What happened for one partner in one run. {@code capped} = more old rows remain; the next run continues. */
    public record PartnerResult(Long partnerId, int removed, int batches, boolean capped) { }

    private final NotificationCleanupRepository repository;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final int maxBatches;

    public NotificationCleanupProcessor(NotificationCleanupRepository repository,
                                        TransactionTemplate tx,
                                        @Value("${app.notifications.cleanup-batch-size:500}") int batchSize,
                                        @Value("${app.notifications.cleanup-max-batches-per-partner:200}") int maxBatches) {
        this.repository = repository;
        this.tx = tx;
        this.batchSize = Math.max(batchSize, 1);
        this.maxBatches = Math.max(maxBatches, 1);
    }

    /** @throws RuntimeException when a batch fails (database problem); earlier batches stay committed. */
    public PartnerResult purgePartner(Long partnerId, LocalDateTime cutoff) {
        MDC.put("partnerId", String.valueOf(partnerId));
        try {
            int removed = 0;
            int batches = 0;
            while (batches < maxBatches) {
                Integer n = tx.execute(status -> {
                    List<Long> ids = repository.oldestIdsForPartner(partnerId, cutoff, PageRequest.of(0, batchSize));
                    return ids.isEmpty() ? 0 : repository.deleteBatch(partnerId, ids);
                });
                int count = n == null ? 0 : n;
                if (count <= 0) {
                    return new PartnerResult(partnerId, removed, batches, false);
                }
                removed += count;
                batches++;
                log.debug("[NOTIFY] cleanup batch partnerId={} batch={} removed={}", partnerId, batches, count);
                if (count < batchSize) {
                    return new PartnerResult(partnerId, removed, batches, false);
                }
            }
            return new PartnerResult(partnerId, removed, batches, true);
        } finally {
            MDC.remove("partnerId");
        }
    }
}
