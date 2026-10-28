package com.solar.Quotation.scheduler;
import com.solar.Quotation.Enum.FollowupStatus;
import com.solar.Quotation.repository.ReminderQueryRepository;
import com.solar.Quotation.service.NotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class FollowupReminderJob {

    private static final Logger log = LoggerFactory.getLogger(FollowupReminderJob.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int PAGE_SIZE = 500;

    private final ReminderQueryRepository queries;
    private final NotificationPublisher publisher;

    public FollowupReminderJob(ReminderQueryRepository queries, NotificationPublisher publisher) {
        this.queries = queries;
        this.publisher = publisher;
    }

    @Scheduled(cron = "${app.notifications.followup-cron:0 0 9 * * *}", zone = "Asia/Kolkata")
    public void run() {
        LocalDate today = LocalDate.now(IST);
        int page = 0;
        int count = 0;
        List<Object[]> rows;
        do {
            rows = queries.duePendingFollowups(FollowupStatus.PENDING, today, PageRequest.of(page++, PAGE_SIZE));
            for (Object[] r : rows) {
                try {
                    publisher.followupReminder(
                            ((Number) r[0]).longValue(),
                            ((Number) r[1]).longValue(),
                            ((Number) r[2]).longValue(),
                            r[3] == null ? null : ((Number) r[3]).longValue(),
                            (String) r[5],
                            (LocalDate) r[4],
                            today);
                    count++;
                } catch (RuntimeException ex) {
                    log.warn("Follow-up reminder failed for follow-up {}", r[0], ex);
                }
            }
        } while (rows.size() == PAGE_SIZE);
        log.info("Follow-up reminder job finished: {} follow-up(s) checked", count);
    }
}
