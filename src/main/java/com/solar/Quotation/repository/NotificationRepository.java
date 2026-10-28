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

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Every query is scoped to the recipient. There is no way to read another user's row through this repository. */
    @Query("select n from Notification n where n.deleted = false and n.recipientUserId = :userId "
            + "and (:unreadOnly = 0 or n.readAt is null) "
            + "and (:type is null or n.type = :type)")
    Page<Notification> search(@Param("userId") Long userId,
                              @Param("unreadOnly") int unreadOnly,
                              @Param("type") NotificationType type,
                              Pageable pageable);

    @Query("select count(n) from Notification n where n.deleted = false "
            + "and n.recipientUserId = :userId and n.readAt is null")
    long countUnread(@Param("userId") Long userId);

    Optional<Notification> findByIdAndRecipientUserIdAndDeletedFalse(Long id, Long recipientUserId);

    boolean existsByRecipientUserIdAndDedupeKey(Long recipientUserId, String dedupeKey);

    /** Returns 1 when the row exists and is the user's (already-read rows keep their original readAt). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = coalesce(n.readAt, :now) "
            + "where n.id = :id and n.recipientUserId = :userId and n.deleted = false")
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now "
            + "where n.recipientUserId = :userId and n.deleted = false and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Housekeeping only (NotificationCleanupJob). Hard delete of old rows. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int purgeOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
