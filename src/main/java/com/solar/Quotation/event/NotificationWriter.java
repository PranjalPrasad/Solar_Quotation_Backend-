package com.solar.Quotation.event;


import com.solar.Quotation.entity.Notification;
import com.solar.Quotation.repository.NotificationRecipientRepository;
import com.solar.Quotation.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores notification drafts, one TENANT at a time, each tenant in its own transaction (REQUIRES_NEW, so it is
 * independent of the request that raised the notification).
 *
 * Tenant rule enforced here (last line of defence, whatever the publisher built): a notification is stored only when
 * its recipient is a real user of the SAME tenant as the notification. Anything else is dropped and logged at WARN.
 * A tenant whose batch fails is retried draft by draft, so one bad row cannot take the others down, and a failure is
 * logged at ERROR but never thrown.
 */
@Component
public class NotificationWriter {

    private static final Logger log = LoggerFactory.getLogger(NotificationWriter.class);

    private final NotificationRepository repository;
    private final NotificationRecipientRepository recipients;
    private final TransactionTemplate tx;

    public NotificationWriter(NotificationRepository repository,
                              NotificationRecipientRepository recipients,
                              PlatformTransactionManager txManager) {
        this.repository = repository;
        this.recipients = recipients;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** What happened to a set of drafts. */
    public record Result(int saved, int duplicates, int dropped, int failed) {
        static Result of(int saved, int duplicates, int dropped, int failed) {
            return new Result(saved, duplicates, dropped, failed);
        }
        Result plus(Result o) {
            return new Result(saved + o.saved, duplicates + o.duplicates, dropped + o.dropped, failed + o.failed);
        }
    }

    /** Never throws. */
    public Result write(List<NotificationDraft> drafts) {
        Result total = Result.of(0, 0, 0, 0);
        if (drafts == null || drafts.isEmpty()) return total;

        Map<Long, List<NotificationDraft>> byPartner = new LinkedHashMap<>();
        int dropped = 0;
        for (NotificationDraft d : drafts) {
            if (d == null) continue;
            if (d.partnerId() == null || d.recipientUserId() == null) {
                dropped++;
                log.error("[NOTIFY] draft dropped (missing tenant or recipient) type={} partnerId={} recipientUserId={}",
                        d.type(), d.partnerId(), d.recipientUserId());
                continue;
            }
            byPartner.computeIfAbsent(d.partnerId(), k -> new java.util.ArrayList<>()).add(d);
        }
        total = total.plus(Result.of(0, 0, dropped, 0));

        for (Map.Entry<Long, List<NotificationDraft>> e : byPartner.entrySet()) {
            total = total.plus(writeTenant(e.getKey(), e.getValue()));
        }
        return total;
    }

    private Result writeTenant(Long partnerId, List<NotificationDraft> group) {
        try {
            return tx.execute(s -> writeInTransaction(partnerId, group));
        } catch (RuntimeException ex) {
            if (group.size() == 1) {
                log.error("[NOTIFY] save failed partnerId={} type={} recipientUserId={}", partnerId,
                        group.get(0).type(), group.get(0).recipientUserId(), ex);
                return Result.of(0, 0, 0, 1);
            }
            log.error("[NOTIFY] save failed partnerId={} drafts={}, retrying one by one", partnerId, group.size(), ex);
            Result r = Result.of(0, 0, 0, 0);
            for (NotificationDraft d : group) {
                try {
                    r = r.plus(tx.execute(s -> writeInTransaction(partnerId, List.of(d))));
                } catch (RuntimeException one) {
                    log.error("[NOTIFY] save failed partnerId={} type={} recipientUserId={}", partnerId,
                            d.type(), d.recipientUserId(), one);
                    r = r.plus(Result.of(0, 0, 0, 1));
                }
            }
            return r;
        }
    }

    private Result writeInTransaction(Long partnerId, List<NotificationDraft> group) {
        Set<Long> wanted = new LinkedHashSet<>();
        for (NotificationDraft d : group) wanted.add(d.recipientUserId());
        Set<Long> inTenant = new HashSet<>(recipients.userIdsInPartner(partnerId, wanted));

        int saved = 0, duplicates = 0, dropped = 0;
        for (NotificationDraft d : group) {
            if (!inTenant.contains(d.recipientUserId())) {
                dropped++;
                log.warn("[NOTIFY] recipient is not a user of the notification's tenant, dropped partnerId={} recipientUserId={} type={}",
                        partnerId, d.recipientUserId(), d.type());
                continue;
            }
            if (d.dedupeKey() != null
                    && repository.existsByChannelPartnerIdAndRecipientUserIdAndDedupeKey(partnerId, d.recipientUserId(), d.dedupeKey())) {
                duplicates++;
                continue;
            }
            Notification n = new Notification();
            n.setRecipientUserId(d.recipientUserId());
            n.setChannelPartnerId(partnerId);
            n.setType(d.type());
            n.setSeverity(d.severity());
            n.setTitle(d.title());
            n.setMessage(d.message());
            n.setEntityType(d.entityType());
            n.setEntityId(d.entityId());
            n.setDedupeKey(d.dedupeKey());
            repository.save(n);
            saved++;
        }
        return Result.of(saved, duplicates, dropped, 0);
    }
}