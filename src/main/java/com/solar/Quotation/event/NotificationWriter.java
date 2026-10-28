package com.solar.Quotation.event;


import com.solar.Quotation.entity.Notification;
import com.solar.Quotation.repository.NotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class NotificationWriter {

    private final NotificationRepository repository;

    public NotificationWriter(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(List<NotificationDraft> drafts) {
        for (NotificationDraft d : drafts) {
            if (d.dedupeKey() != null
                    && repository.existsByRecipientUserIdAndDedupeKey(d.recipientUserId(), d.dedupeKey())) {
                continue;
            }
            Notification n = new Notification();
            n.setRecipientUserId(d.recipientUserId());
            n.setChannelPartnerId(d.partnerId());
            n.setType(d.type());
            n.setSeverity(d.severity());
            n.setTitle(d.title());
            n.setMessage(d.message());
            n.setEntityType(d.entityType());
            n.setEntityId(d.entityId());
            n.setDedupeKey(d.dedupeKey());
            repository.save(n);
        }
    }
}
