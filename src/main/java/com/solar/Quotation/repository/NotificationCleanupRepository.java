package com.solar.Quotation.repository;

import com.solar.Quotation.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Housekeeping queries for NotificationCleanupJob. Everything after the partner list is pinned to ONE partner. */
public interface NotificationCleanupRepository extends Repository<Notification, Long> {

    /** Tenants (PLATFORM included, any partner status) that still hold rows older than the cutoff. */
    @Query("select distinct n.channelPartnerId from Notification n where n.createdAt < :cutoff "
            + "order by n.channelPartnerId")
    List<Long> partnerIdsWithRowsOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /** One batch for ONE partner. Deleted rows leave the result, so the next call simply returns the next batch. */
    @Query("select n.id from Notification n where n.channelPartnerId = :partnerId and n.createdAt < :cutoff "
            + "order by n.id")
    List<Long> oldestIdsForPartner(@Param("partnerId") Long partnerId,
                                   @Param("cutoff") LocalDateTime cutoff,
                                   Pageable pageable);

    /** Hard delete of one batch. partnerId is repeated so an id of another tenant can never be removed here. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.channelPartnerId = :partnerId and n.id in :ids")
    int deleteBatch(@Param("partnerId") Long partnerId, @Param("ids") List<Long> ids);
}
