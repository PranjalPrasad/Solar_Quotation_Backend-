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
import com.solar.Quotation.repository.NotificationRecipientRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.NotificationPublisher;
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
import java.util.Set;

@Service
public class NotificationPublisherImpl implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisherImpl.class);

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String PARTNER_ADMIN = "CHANNEL_PARTNER_ADMIN";

    private final ApplicationEventPublisher events;
    private final NotificationRecipientRepository recipients;
    private final CustomerRepository customerRepository;

    public NotificationPublisherImpl(ApplicationEventPublisher events,
                                     NotificationRecipientRepository recipients,
                                     CustomerRepository customerRepository) {
        this.events = events;
        this.recipients = recipients;
        this.customerRepository = customerRepository;
    }

    @Override
    public void publish(List<NotificationDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) return;
        events.publishEvent(new NotificationRequestedEvent(List.copyOf(drafts)));
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
            log.warn("Could not build quotation-created notification for quotation {}", q.getId(), ex);
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

            // QuotationServiceImpl turns an accepted referred quotation into a PENDING referral: Super Admin pays/approves it
            if (now == QuotationStatus.ACCEPTED && q.isReferred() && q.getReferralStatus() == ReferralStatus.PENDING) {
                Set<Long> admins = new LinkedHashSet<>(
                        recipients.activeUserIdsByRole(SUPER_ADMIN, RecordStatus.ACTIVE));
                admins.remove(actorId());
                String message = q.getCode() + " was accepted and has a referral of Rs. "
                        + (q.getReferralAmount() == null ? "0" : q.getReferralAmount().toPlainString())
                        + (q.getReferredBy() == null || q.getReferredBy().isBlank() ? "" : " (" + q.getReferredBy() + ")");
                publish(drafts(admins, q.getChannelPartnerId(), NotificationType.REFERRAL_PENDING,
                        NotificationSeverity.INFO, "Referral awaiting approval", message,
                        "QUOTATION", q.getId(), "REFERRAL_PENDING:" + q.getId()));
            }
        } catch (RuntimeException ex) {
            log.warn("Could not build quotation-status notification for quotation {}", q.getId(), ex);
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
            log.warn("Could not build customer-created notification for customer {}", c.getId(), ex);
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
            log.warn("Could not build project-status notification for project {}", p.getId(), ex);
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
            log.warn("Could not build follow-up reminder for follow-up {}", followupId, ex);
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
            log.warn("Could not build quotation-expiry reminder for quotation {}", quotationId, ex);
        }
    }

    // ---------------------------------------------------------------- helpers

    private Set<Long> partnerAdmins(Long partnerId) {
        return new LinkedHashSet<>(recipients.activeUserIdsByPartnerAndRole(partnerId, PARTNER_ADMIN, RecordStatus.ACTIVE));
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

    private static List<NotificationDraft> drafts(Collection<Long> to, Long partnerId, NotificationType type,
                                                  NotificationSeverity severity, String title, String message,
                                                  String entityType, Long entityId, String dedupeKey) {
        List<NotificationDraft> out = new ArrayList<>();
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
