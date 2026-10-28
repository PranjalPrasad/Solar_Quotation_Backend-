package com.solar.Quotation.scheduler;

import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.repository.ReminderQueryRepository;
import com.solar.Quotation.service.NotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Every morning: PENDING quotations whose validity ends within the next few days. */
@Component
public class QuotationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(QuotationExpiryJob.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int PAGE_SIZE = 500;

    private final ReminderQueryRepository queries;
    private final NotificationPublisher publisher;

    @Value("${app.notifications.expiry-warning-days:3}")
    private int warningDays;

    public QuotationExpiryJob(ReminderQueryRepository queries, NotificationPublisher publisher) {
        this.queries = queries;
        this.publisher = publisher;
    }

    @Scheduled(cron = "${app.notifications.expiry-cron:0 15 9 * * *}", zone = "Asia/Kolkata")
    public void run() {
        LocalDate today = LocalDate.now(IST);
        LocalDate until = today.plusDays(Math.max(warningDays, 0));
        int page = 0;
        int count = 0;
        List<Object[]> rows;
        do {
            rows = queries.expiringQuotations(QuotationStatus.PENDING, today, until, PageRequest.of(page++, PAGE_SIZE));
            for (Object[] r : rows) {
                try {
                    publisher.quotationExpiring(
                            ((Number) r[0]).longValue(),
                            ((Number) r[1]).longValue(),
                            (String) r[2],
                            r[3] == null ? null : ((Number) r[3]).longValue(),
                            (String) r[5],
                            (LocalDate) r[4],
                            today);
                    count++;
                } catch (RuntimeException ex) {
                    log.warn("Quotation expiry reminder failed for quotation {}", r[0], ex);
                }
            }
        } while (rows.size() == PAGE_SIZE);
        log.info("Quotation expiry job finished: {} quotation(s) checked", count);
    }
}
