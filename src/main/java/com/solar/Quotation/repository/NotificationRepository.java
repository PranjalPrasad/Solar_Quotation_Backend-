package com.solar.Quotation.repository;


import com.solar.Quotation.Enum.NotificationType;
import com.solar.Quotation.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Every method takes the caller's OWN tenant (partnerId) AND the recipient (userId). There is no way to read or
 * change another tenant's row, or another user's row, through this repository.
 * Housekeeping (hard delete of old rows) lives in NotificationCleanupRepository.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n where n.deleted = false and n.channelPartnerId = :partnerId "
            + "and n.recipientUserId = :userId "
            + "and (:unreadOnly = 0 or n.readAt is null) "
            + "and (:type is null or n.type = :type)")
    Page<Notification> search(@Param("partnerId") Long partnerId,
                              @Param("userId") Long userId,
                              @Param("unreadOnly") int unreadOnly,
                              @Param("type") NotificationType type,
                              Pageable pageable);

    @Query("select count(n) from Notification n where n.deleted = false and n.channelPartnerId = :partnerId "
            + "and n.recipientUserId = :userId and n.readAt is null")
    long countUnread(@Param("partnerId") Long partnerId, @Param("userId") Long userId);

    Optional<Notification> findByIdAndChannelPartnerIdAndRecipientUserIdAndDeletedFalse(Long id, Long channelPartnerId,
                                                                                        Long recipientUserId);

    boolean existsByChannelPartnerIdAndRecipientUserIdAndDedupeKey(Long channelPartnerId, Long recipientUserId,
                                                                   String dedupeKey);

    /** Returns 1 when the row exists and is the user's in this tenant (already-read rows keep their original readAt). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = coalesce(n.readAt, :now) "
            + "where n.id = :id and n.channelPartnerId = :partnerId and n.recipientUserId = :userId and n.deleted = false")
    int markRead(@Param("id") Long id, @Param("partnerId") Long partnerId, @Param("userId") Long userId,
                 @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now "
            + "where n.channelPartnerId = :partnerId and n.recipientUserId = :userId "
            + "and n.deleted = false and n.readAt is null")
    int markAllRead(@Param("partnerId") Long partnerId, @Param("userId") Long userId, @Param("now") LocalDateTime now);
}
