package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.NotificationSeverity;
import com.solar.Quotation.Enum.NotificationType;
import com.solar.Quotation.Enum.ProjectStatus;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.Enum.ReferralStatus;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.Project;
import com.solar.Quotation.entity.Quotation;
import com.solar.Quotation.event.NotificationDraft;
import com.solar.Quotation.event.NotificationRequestedEvent;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.repository.NotificationRecipientRepository;
import com.solar.Quotation.security.Actors;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.NotificationPublisher;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.PlatformTenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationPublisherImpl implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisherImpl.class);

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String PARTNER_ADMIN = "CHANNEL_PARTNER_ADMIN";

    private final ApplicationEventPublisher events;
    private final NotificationRecipientRepository recipients;
    private final CustomerRepository customerRepository;
    private final PlatformTenant platform;
    private final PartnerRefProvider partnerRefs;

    public NotificationPublisherImpl(ApplicationEventPublisher events,
                                     NotificationRecipientRepository recipients,
                                     CustomerRepository customerRepository,
                                     PlatformTenant platform,
                                     PartnerRefProvider partnerRefs) {
        this.events = events;
        this.recipients = recipients;
        this.customerRepository = customerRepository;
        this.platform = platform;
        this.partnerRefs = partnerRefs;
    }

    /** Never throws: a notification problem is logged at ERROR and the caller's work carries on. */
    @Override
    public void publish(List<NotificationDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) return;
        try {
            events.publishEvent(new NotificationRequestedEvent(List.copyOf(drafts)));
            log.debug("[NOTIFY] queued {} {}", summary(drafts), Actors.describe());
        } catch (RuntimeException ex) {
            log.error("[NOTIFY] publish failed {} {}", summary(drafts), Actors.describe(), ex);
        }
    }

    // ---------------------------------------------------------------- quotation

    @Override
    public void quotationCreated(Quotation q) {
        try {
            Set<Long> to = partnerAdmins(q.getChannelPartnerId());
            to.remove(actorId());
            String message = q.getCode() + " for " + customerName(q.getCustomerId())
                    + " (" + kw(q.getSystemSizeKw()) + " kW) was created by " + actorName();
            publish(drafts(to, q.getChannelPartnerId(), NotificationType.QUOTATION_CREATED,
                    NotificationSeverity.INFO, "New quotation created", message,
                    "QUOTATION", q.getId(), null));
        } catch (RuntimeException ex) {
            buildFailed("quotation-created", q.getChannelPartnerId(), q.getId(), ex);
        }
    }

    @Override
    public void quotationStatusChanged(Quotation q, QuotationStatus previous) {
        try {
            QuotationStatus now = q.getStatus();
            if (now == previous) return;

            if (now == QuotationStatus.ACCEPTED || now == QuotationStatus.REJECTED) {
                boolean accepted = now == QuotationStatus.ACCEPTED;
                Set<Long> to = partnerAdmins(q.getChannelPartnerId());
                if (q.getCreatedBy() != null) to.add(q.getCreatedBy());
                to.remove(actorId());
                String message = q.getCode() + " for " + customerName(q.getCustomerId())
                        + " was " + (accepted ? "accepted" : "rejected") + " by " + actorName();
                publish(drafts(to, q.getChannelPartnerId(),
                        accepted ? NotificationType.QUOTATION_ACCEPTED : NotificationType.QUOTATION_REJECTED,
                        accepted ? NotificationSeverity.SUCCESS : NotificationSeverity.WARNING,
                        accepted ? "Quotation accepted" : "Quotation rejected", message,
                        "QUOTATION", q.getId(), null));
            }

            // QuotationServiceImpl turns an accepted referred quotation into a PENDING referral: Super Admin pays/approves it.
            // The notice belongs to the PLATFORM tenant (the Super Admins' own tenant), NOT to the partner that raised it;
            // the partner is named in the message and the quotation is reachable through entityType/entityId.
            if (now == QuotationStatus.ACCEPTED && q.isReferred() && q.getReferralStatus() == ReferralStatus.PENDING) {
                Long platformId = platform.id();
                Set<Long> admins = new LinkedHashSet<>(
                        recipients.activeUserIdsByPartnerAndRole(platformId, SUPER_ADMIN, RecordStatus.ACTIVE));
                admins.remove(actorId());
                String from = partnerName(q.getChannelPartnerId());
                String message = q.getCode() + (from == null ? "" : " of " + from) + " was accepted and has a referral of Rs. "
                        + (q.getReferralAmount() == null ? "0" : q.getReferralAmount().toPlainString())
                        + (q.getReferredBy() == null || q.getReferredBy().isBlank() ? "" : " (" + q.getReferredBy() + ")");
                publish(drafts(admins, platformId, NotificationType.REFERRAL_PENDING,
                        NotificationSeverity.INFO, "Referral awaiting approval", message,
                        "QUOTATION", q.getId(), "REFERRAL_PENDING:" + q.getId()));
            }
        } catch (RuntimeException ex) {
            buildFailed("quotation-status", q.getChannelPartnerId(), q.getId(), ex);
        }
    }

    // ---------------------------------------------------------------- customer / project

    @Override
    public void customerCreated(Customer c) {
        try {
            Set<Long> to = partnerAdmins(c.getChannelPartnerId());
            to.remove(actorId());
            publish(drafts(to, c.getChannelPartnerId(), NotificationType.CUSTOMER_CREATED,
                    NotificationSeverity.INFO, "New customer added",
                    c.getName() + " was added by " + actorName(),
                    "CUSTOMER", c.getId(), null));
        } catch (RuntimeException ex) {
            buildFailed("customer-created", c.getChannelPartnerId(), c.getId(), ex);
        }
    }

    @Override
    public void projectStatusChanged(Project p, ProjectStatus previous) {
        try {
            if (p.getStatus() == previous) return;
            Set<Long> to = partnerAdmins(p.getChannelPartnerId());
            if (p.getCreatedBy() != null) to.add(p.getCreatedBy());
            to.remove(actorId());
            NotificationSeverity severity = p.getStatus() == ProjectStatus.COMPLETED ? NotificationSeverity.SUCCESS
                    : p.getStatus() == ProjectStatus.ON_HOLD ? NotificationSeverity.WARNING
                    : NotificationSeverity.INFO;
            String message = p.getName() + " moved from " + pretty(previous) + " to " + pretty(p.getStatus())
                    + " (by " + actorName() + ")";
            publish(drafts(to, p.getChannelPartnerId(), NotificationType.PROJECT_STATUS_CHANGED,
                    severity, "Project status updated", message,
                    "PROJECT", p.getId(), null));
        } catch (RuntimeException ex) {
            buildFailed("project-status", p.getChannelPartnerId(), p.getId(), ex);
        }
    }

    // ---------------------------------------------------------------- reminders (scheduled jobs)

    @Override
    public void followupReminder(Long followupId, Long partnerId, Long customerId, Long createdBy,
                                 String customerName, LocalDate nextDate, LocalDate today) {
        try {
            boolean overdue = nextDate.isBefore(today);
            Set<Long> to = new LinkedHashSet<>();
            if (createdBy != null) to.add(createdBy); else to.addAll(partnerAdmins(partnerId));

            NotificationType type = overdue ? NotificationType.FOLLOWUP_OVERDUE : NotificationType.FOLLOWUP_DUE;
            String message = overdue
                    ? customerName + " - follow-up was due on " + nextDate
                    : customerName + " - follow-up is scheduled for today";
            // nextDate is part of the key: if the follow-up is rescheduled the user is reminded again
            publish(drafts(to, partnerId, type,
                    overdue ? NotificationSeverity.WARNING : NotificationSeverity.INFO,
                    overdue ? "Follow-up overdue" : "Follow-up due today", message,
                    "CUSTOMER", customerId, type.name() + ":" + followupId + ":" + nextDate));
        } catch (RuntimeException ex) {
            buildFailed("followup-reminder", partnerId, followupId, ex);
        }
    }

    @Override
    public void quotationExpiring(Long quotationId, Long partnerId, String code, Long createdBy,
                                  String customerName, LocalDate validUntil, LocalDate today) {
        try {
            long days = ChronoUnit.DAYS.between(today, validUntil);
            Set<Long> to = new LinkedHashSet<>();
            if (createdBy != null) to.add(createdBy); else to.addAll(partnerAdmins(partnerId));

            String when = days <= 0 ? "expires today" : "expires in " + days + (days == 1 ? " day" : " days");
            publish(drafts(to, partnerId, NotificationType.QUOTATION_EXPIRING,
                    NotificationSeverity.WARNING, "Quotation expiring soon",
                    code + " for " + customerName + " " + when,
                    "QUOTATION", quotationId, "QUOTATION_EXPIRING:" + quotationId + ":" + validUntil));
        } catch (RuntimeException ex) {
            buildFailed("quotation-expiry", partnerId, quotationId, ex);
        }
    }

    // ---------------------------------------------------------------- helpers

    private Set<Long> partnerAdmins(Long partnerId) {
        return new LinkedHashSet<>(recipients.activeUserIdsByPartnerAndRole(partnerId, PARTNER_ADMIN, RecordStatus.ACTIVE));
    }

    /** Partner name for a message; a lookup problem only drops the name, never the notification. */
    private String partnerName(Long partnerId) {
        try {
            PartnerRefResponseDto ref = partnerRefs.ref(partnerId);
            return ref == null ? null : ref.getName();
        } catch (RuntimeException ex) {
            log.warn("[NOTIFY] partner name lookup failed partnerId={}", partnerId, ex);
            return null;
        }
    }

    private String customerName(Long customerId) {
        return customerRepository.findById(customerId).map(Customer::getName).orElse("customer");
    }

    private static Long actorId() {
        CurrentUser u = TenantContext.get();
        return u == null ? null : u.getUserId();
    }

    private static String actorName() {
        CurrentUser u = TenantContext.get();
        return u == null || u.getName() == null ? "system" : u.getName();
    }

    private static String kw(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private static String pretty(Enum<?> e) {
        return e == null ? "-" : e.name().toLowerCase().replace('_', ' ');
    }

    /** ERROR, ids only (no names, no message text). The caller's request is not affected. */
    private static void buildFailed(String what, Long partnerId, Long entityId, RuntimeException ex) {
        log.error("[NOTIFY] publish failed (could not build) what={} partnerId={} entityId={} {}",
                what, partnerId, entityId, Actors.describe(), ex);
    }

    /** "type=X drafts=N partnerIds=[..]" - null safe, because it is used inside catch blocks. */
    private static String summary(List<NotificationDraft> drafts) {
        List<NotificationDraft> ok = drafts.stream().filter(Objects::nonNull).toList();
        String type = ok.isEmpty() ? "-" : String.valueOf(ok.get(0).type());
        Set<Long> partners = ok.stream().map(NotificationDraft::partnerId).collect(Collectors.toCollection(LinkedHashSet::new));
        return "type=" + type + " drafts=" + drafts.size() + " partnerIds=" + partners;
    }

    private static List<NotificationDraft> drafts(Collection<Long> to, Long partnerId, NotificationType type,
                                                  NotificationSeverity severity, String title, String message,
                                                  String entityType, Long entityId, String dedupeKey) {
        List<NotificationDraft> out = new ArrayList<>();
        if (partnerId == null) {   // channel_partner_id is NOT NULL: a draft without a tenant can never be stored
            log.error("[NOTIFY] publish failed (no partnerId) type={} entityType={} entityId={} recipients={}",
                    type, entityType, entityId, to == null ? 0 : to.size());
            return out;
        }
        for (Long userId : to) {
            if (userId == null) continue;
            out.add(new NotificationDraft(userId, partnerId, type, severity,
                    cut(title, 150), cut(message, 500), entityType, entityId, cut(dedupeKey, 120)));
        }
        return out;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
