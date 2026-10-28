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
 * Read-only aggregate queries that exist only for the dashboard (same pattern as ReminderQueryRepository).
 * Plain counts and sums that already exist (QuotationRepository.countFiltered / sumTotal,
 * CustomerRepository.countFiltered, ProjectRepository.countFiltered, ReferralRepository.statusTotals)
 * are reused by DashboardServiceImpl, not repeated here.
 *
 * partnerId null = all partners (Super Admin only); ownerId null = whole partner (set only for data scope OWN).
 * DashboardServiceImpl gets both from TenantResolver.
 */
public interface DashboardQueryRepository extends Repository<Quotation, Long> {

    /** Quotations dated within [fromDate, toDate]. */
    @Query("select count(q) from Quotation q where q.deleted = false "
            + "and (:partnerId is null or q.channelPartnerId = :partnerId) "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "and q.quoteDate >= :fromDate and q.quoteDate <= :toDate")
    long countBetween(@Param("partnerId") Long partnerId,
                      @Param("ownerId") Long ownerId,
                      @Param("fromDate") LocalDate fromDate,
                      @Param("toDate") LocalDate toDate);

    @Query("select coalesce(sum(q.totalAmount), 0) from Quotation q where q.deleted = false "
            + "and (:partnerId is null or q.channelPartnerId = :partnerId) "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "and q.quoteDate >= :fromDate and q.quoteDate <= :toDate")
    BigDecimal sumBetween(@Param("partnerId") Long partnerId,
                          @Param("ownerId") Long ownerId,
                          @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate);

    /** One row: [small, medium, large] counts. small <= smallMax, medium <= mediumMax, large above that. */
    @Query("select count(case when q.systemSizeKw <= :smallMax then 1 end), "
            + "count(case when q.systemSizeKw > :smallMax and q.systemSizeKw <= :mediumMax then 1 end), "
            + "count(case when q.systemSizeKw > :mediumMax then 1 end) "
            + "from Quotation q where q.deleted = false "
            + "and (:partnerId is null or q.channelPartnerId = :partnerId) "
            + "and (:ownerId is null or q.createdBy = :ownerId)")
    List<Object[]> sizeBuckets(@Param("partnerId") Long partnerId,
                               @Param("ownerId") Long ownerId,
                               @Param("smallMax") BigDecimal smallMax,
                               @Param("mediumMax") BigDecimal mediumMax);

    /** Row: [code, customerName, channelPartnerId, systemSizeKw, totalAmount, status, quoteDate]. Newest first. */
    @Query("select q.code, c.name, q.channelPartnerId, q.systemSizeKw, q.totalAmount, q.status, q.quoteDate "
            + "from Quotation q, Customer c "
            + "where q.customerId = c.id and q.deleted = false "
            + "and (:partnerId is null or q.channelPartnerId = :partnerId) "
            + "and (:ownerId is null or q.createdBy = :ownerId) "
            + "order by q.createdAt desc, q.id desc")
    List<Object[]> recent(@Param("partnerId") Long partnerId,
                          @Param("ownerId") Long ownerId,
                          Pageable pageable);

    /** Row: [channelPartnerId, quotationCount, sum(totalAmount)], highest revenue first. Super Admin only. */
    @Query("select q.channelPartnerId, count(q), coalesce(sum(q.totalAmount), 0) "
            + "from Quotation q where q.deleted = false "
            + "group by q.channelPartnerId "
            + "order by coalesce(sum(q.totalAmount), 0) desc, count(q) desc")
    List<Object[]> topPartners(Pageable pageable);

    @Query("select count(p) from ChannelPartner p where p.deleted = false and p.partnerType = :type "
            + "and (:status is null or p.status = :status)")
    long countPartners(@Param("type") PartnerType type, @Param("status") RecordStatus status);
}
