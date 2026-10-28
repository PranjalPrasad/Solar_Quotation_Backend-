package com.solar.Quotation.service;

import com.solar.Quotation.Enum.ProjectStatus;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.Project;
import com.solar.Quotation.entity.Quotation;
import com.solar.Quotation.event.NotificationDraft;

import java.time.LocalDate;
import java.util.List;

/**
 * Used by the other services/jobs to raise notifications. Nothing here throws: if a notification cannot be
 * built or saved it is logged and skipped, the caller's work is never affected.
 */
public interface NotificationPublisher {

    /** Low level: queue ready-made drafts. They are saved after the current transaction commits. */
    void publish(List<NotificationDraft> drafts);

    /** Partner admins of the quotation's partner (except the person who did it). */
    void quotationCreated(Quotation q);

    /** ACCEPTED / REJECTED -> partner admins + quotation creator. Accepted referred quotation -> Super Admins too. */
    void quotationStatusChanged(Quotation q, QuotationStatus previous);

    /** Partner admins of the customer's partner (except the person who added it). */
    void customerCreated(Customer c);

    /** Partner admins + project creator (except the person who changed it). */
    void projectStatusChanged(Project p, ProjectStatus previous);

    /** Used by FollowupReminderJob. nextDate before today = overdue, otherwise due today. */
    void followupReminder(Long followupId, Long partnerId, Long customerId, Long createdBy,
                          String customerName, LocalDate nextDate, LocalDate today);

    /** Used by QuotationExpiryJob. */
    void quotationExpiring(Long quotationId, Long partnerId, String code, Long createdBy,
                           String customerName, LocalDate validUntil, LocalDate today);
}
