package com.solar.Quotation.event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationWriter writer;

    public NotificationEventListener(NotificationWriter writer) {
        this.writer = writer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationRequested(NotificationRequestedEvent event) {
        try {
            NotificationWriter.Result r = writer.write(event.drafts());
            log.debug("[NOTIFY] stored saved={} duplicates={} dropped={} failed={}",
                    r.saved(), r.duplicates(), r.dropped(), r.failed());
        } catch (RuntimeException ex) {
            // a notification problem must never surface to the user who did the real work
            log.error("[NOTIFY] save failed drafts={}", event.drafts().size(), ex);
        }
    }
}
