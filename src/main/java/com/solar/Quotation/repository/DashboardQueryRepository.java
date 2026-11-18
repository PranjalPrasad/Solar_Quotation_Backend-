package com.solar.Quotation.repository;

import com.solar.Quotation.Enum.PartnerType;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.Quotation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only aggregate queries that exist only for the dashboard.
 *
 * TENANT RULE (checked by DashboardQueryScopeContractTest):
 *   - Every method WITHOUT the "platform" prefix takes a NON-NULL :partnerId and filters with plain equality
 *     (no "(:partnerId is null or ...)"). So a tenant dashboard can never turn into an all-partners query by a null
 *     slipping through, and MySQL can use the (channel_partner_id, deleted, ...) indexes.
 *   - Cross-partner totals live ONLY in the "platform*" methods, which only the Super Admin endpoint
 *     (DashboardServiceImpl.adminSummary) calls.
 *   - :ownerId is null except for data scope OWN (TenantResolver.ownerFilter()).
 *
 * Index support: see the idx_*_dash_* indexes on Quotation / Customer / Project / Referral and V101__dashboard_indexes.sql.
 */
public interface DashboardQueryRepository extends Repository<Quotation, Long> {

    // ===================================================================== tenant scoped

    /** Rows of [status, count, sum(totalAmount)], one per status. One query gives total, pending, accepted, rejected and value. */
    @Query("select q.status, count(q), coalesce(sum(q.totalAmount), 0) from Quotation q "
            + "where q.channelPartnerId = :partnerId and q.deleted = false "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "group by q.status")
    List<Object[]> statusTotals(@Param("partnerId") Long partnerId, @Param("ownerId") Long ownerId);

    /** One row: [quotations dated today, quotations dated in [monthStart, nextMonth), their total value]. */
    @Query("select count(case when q.quoteDate = :today then 1 end), count(q), coalesce(sum(q.totalAmount), 0) "
            + "from Quotation q "
            + "where q.channelPartnerId = :partnerId and q.deleted = false "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "and q.quoteDate >= :monthStart and q.quoteDate < :nextMonth")
    List<Object[]> periodTotals(@Param("partnerId") Long partnerId, @Param("ownerId") Long ownerId,
                                @Param("today") LocalDate today,
                                @Param("monthStart") LocalDate monthStart, @Param("nextMonth") LocalDate nextMonth);

    /** One row: [small, medium, large] counts. small <= smallMax, medium <= mediumMax, large above that. */
    @Query("select count(case when q.systemSizeKw <= :smallMax then 1 end), "
            + "count(case when q.systemSizeKw > :smallMax and q.systemSizeKw <= :mediumMax then 1 end), "
            + "count(case when q.systemSizeKw > :mediumMax then 1 end) "
            + "from Quotation q "
            + "where q.channelPartnerId = :partnerId and q.deleted = false "
            + "and (:ownerId is null or q.createdBy = :ownerId)")
    List<Object[]> sizeBuckets(@Param("partnerId") Long partnerId, @Param("ownerId") Long ownerId,
                               @Param("smallMax") BigDecimal smallMax, @Param("mediumMax") BigDecimal mediumMax);

    /** Rows of [yyyy-MM, count, sum(totalAmount)] for the optional range, oldest month first. */
    @Query("select function('date_format', q.quoteDate, '%Y-%m'), count(q), coalesce(sum(q.totalAmount), 0) "
            + "from Quotation q "
            + "where q.channelPartnerId = :partnerId and q.deleted = false "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "and (:fromDate is null or q.quoteDate >= :fromDate) "
            + "and (:toDate is null or q.quoteDate <= :toDate) "
            + "group by function('date_format', q.quoteDate, '%Y-%m') "
            + "order by 1")
    List<Object[]> monthlyTrend(@Param("partnerId") Long partnerId, @Param("ownerId") Long ownerId,
                                @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("select count(c) from Customer c "
            + "where c.channelPartnerId = :partnerId and c.deleted = false "
            + "and (:ownerId is null or c.createdBy = :ownerId)")
    long countCustomers(@Param("partnerId") Long partnerId, @Param("ownerId") Long ownerId);

    @Query("select count(p) from Project p "
            + "where p.channelPartnerId = :partnerId and p.deleted = false "
            + "and (:ownerId is null or p.createdBy = :ownerId)")
    long countProjects(@Param("partnerId") Long partnerId, @Param("ownerId") Long ownerId);

    /** Rows of [referral status, sum(referralAmount)] where THIS partner is the referrer. Income = APPROVED + PAID. */
    @Query("select r.status, coalesce(sum(r.referralAmount), 0) from Referral r "
            + "where r.referrerPartnerId = :partnerId and r.deleted = false "
            + "group by r.status")
    List<Object[]> referralTotalsAsReferrer(@Param("partnerId") Long partnerId);

    /**
     * Row: [code, customerName, systemSizeKw, totalAmount, status, quoteDate]. Newest first; the Pageable is the LIMIT.
     * The customer join also matches the partner, so a customer of another tenant can never be named here.
     */
    @Query("select q.code, c.name, q.systemSizeKw, q.totalAmount, q.status, q.quoteDate "
            + "from Quotation q left join Customer c on c.id = q.customerId and c.channelPartnerId = q.channelPartnerId "
            + "where q.channelPartnerId = :partnerId and q.deleted = false "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "order by q.createdAt desc, q.id desc")
    List<Object[]> recent(@Param("partnerId") Long partnerId, @Param("ownerId") Long ownerId, Pageable pageable);

    // ===================================================================== platform wide (Super Admin endpoint only)

    /** One row: [count, sum(totalAmount)] over every tenant. */
    @Query("select count(q), coalesce(sum(q.totalAmount), 0) from Quotation q where q.deleted = false")
    List<Object[]> platformQuotationTotals();

    /**
     * Rows of [partnerId, partnerName, partnerCode, quotationCount, revenue], highest revenue first. Only real partners
     * (type = PARTNER): the PLATFORM tenant is not a "partner". Names come with the row, so there is no second lookup.
     */
    @Query("select q.channelPartnerId, p.name, p.code, count(q), coalesce(sum(q.totalAmount), 0) "
            + "from Quotation q, ChannelPartner p "
            + "where p.id = q.channelPartnerId and p.deleted = false and p.partnerType = :type "
            + "and q.deleted = false "
            + "group by q.channelPartnerId, p.name, p.code "
            + "order by coalesce(sum(q.totalAmount), 0) desc, count(q) desc")
    List<Object[]> platformTopPartners(@Param("type") PartnerType type, Pageable pageable);

    @Query("select count(p) from ChannelPartner p where p.deleted = false and p.partnerType = :type "
            + "and (:status is null or p.status = :status)")
    long platformCountPartners(@Param("type") PartnerType type, @Param("status") RecordStatus status);

    /** Referral commission that is approved but not yet paid out, over every tenant. */
    @Query("select coalesce(sum(r.referralAmount), 0) from Referral r "
            + "where r.deleted = false and r.status = com.solar.Quotation.Enum.ReferralStatus.APPROVED")
    BigDecimal platformPendingReferralPayouts();
}