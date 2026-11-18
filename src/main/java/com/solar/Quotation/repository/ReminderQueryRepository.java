package com.solar.Quotation.repository;


import com.solar.Quotation.Enum.FollowupStatus;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.entity.CustomerFollowup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReminderQueryRepository extends Repository<CustomerFollowup, Long> {

    /**
     * Partners that have at least one pending follow-up due today or earlier. Only ACTIVE, not deleted partners,
     * and only follow-ups whose customer belongs to the SAME partner (a mismatching row is never reminded).
     */
    @Query("select distinct f.channelPartnerId "
            + "from CustomerFollowup f, Customer c, ChannelPartner p "
            + "where f.customerId = c.id and c.channelPartnerId = f.channelPartnerId "
            + "and p.id = f.channelPartnerId and p.deleted = false and p.status = :partnerStatus "
            + "and f.deleted = false and c.deleted = false "
            + "and f.status = :status and f.nextDate is not null and f.nextDate <= :today "
            + "order by f.channelPartnerId")
    List<Long> partnersWithDueFollowups(@Param("status") FollowupStatus status,
                                        @Param("partnerStatus") RecordStatus partnerStatus,
                                        @Param("today") LocalDate today);

    /**
     * ONE partner, one batch, keyset paging (id > afterId) so a batch never skips or repeats rows.
     * Row: [followupId, channelPartnerId, customerId, createdBy, nextDate, customerName].
     */
    @Query("select f.id, f.channelPartnerId, f.customerId, f.createdBy, f.nextDate, c.name "
            + "from CustomerFollowup f, Customer c "
            + "where f.customerId = c.id and c.channelPartnerId = f.channelPartnerId "
            + "and f.channelPartnerId = :partnerId and f.id > :afterId "
            + "and f.deleted = false and c.deleted = false "
            + "and f.status = :status and f.nextDate is not null and f.nextDate <= :today "
            + "order by f.id")
    List<Object[]> duePendingFollowupsForPartner(@Param("partnerId") Long partnerId,
                                                 @Param("status") FollowupStatus status,
                                                 @Param("today") LocalDate today,
                                                 @Param("afterId") Long afterId,
                                                 Pageable pageable);

    /**
     * Partners that have at least one PENDING quotation whose validity ends inside the window. Only ACTIVE, not deleted
     * partners, and only quotations whose customer belongs to the SAME partner.
     */
    @Query("select distinct q.channelPartnerId "
            + "from Quotation q, Customer c, ChannelPartner p "
            + "where q.customerId = c.id and c.channelPartnerId = q.channelPartnerId "
            + "and p.id = q.channelPartnerId and p.deleted = false and p.status = :partnerStatus "
            + "and q.deleted = false and c.deleted = false "
            + "and q.status = :status and q.validUntil >= :today and q.validUntil <= :until "
            + "order by q.channelPartnerId")
    List<Long> partnersWithExpiringQuotations(@Param("status") QuotationStatus status,
                                              @Param("partnerStatus") RecordStatus partnerStatus,
                                              @Param("today") LocalDate today,
                                              @Param("until") LocalDate until);

    /**
     * ONE partner, one batch, keyset paging (id > afterId) so a batch never skips or repeats rows.
     * Row: [quotationId, channelPartnerId, code, createdBy, validUntil, customerName].
     */
    @Query("select q.id, q.channelPartnerId, q.code, q.createdBy, q.validUntil, c.name "
            + "from Quotation q, Customer c "
            + "where q.customerId = c.id and c.channelPartnerId = q.channelPartnerId "
            + "and q.channelPartnerId = :partnerId and q.id > :afterId "
            + "and q.deleted = false and c.deleted = false "
            + "and q.status = :status and q.validUntil >= :today and q.validUntil <= :until "
            + "order by q.id")
    List<Object[]> expiringQuotationsForPartner(@Param("partnerId") Long partnerId,
                                                @Param("status") QuotationStatus status,
                                                @Param("today") LocalDate today,
                                                @Param("until") LocalDate until,
                                                @Param("afterId") Long afterId,
                                                Pageable pageable);
}