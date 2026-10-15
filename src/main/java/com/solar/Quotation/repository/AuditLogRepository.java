package com.solar.Quotation.repository;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("select a from AuditLog a where a.deleted = false "
            + "and (:partnerId is null or a.channelPartnerId = :partnerId) "
            + "and (:userId is null or a.actorUserId = :userId) "
            + "and (:module is null or a.module = :module) "
            + "and (:action is null or a.action = :action) "
            + "and (:entityType is null or a.entityType = :entityType) "
            + "and (:entityId is null or a.entityId = :entityId) "
            + "and a.createdAt >= :fromTs and a.createdAt < :toTs "
            + "and (:search is null or lower(a.entityLabel) like lower(concat('%', :search, '%')) "
            + "     or lower(a.actorName) like lower(concat('%', :search, '%')))")
    Page<AuditLog> search(@Param("partnerId") Long partnerId,
                          @Param("userId") Long userId,
                          @Param("module") String module,
                          @Param("action") AuditAction action,
                          @Param("entityType") String entityType,
                          @Param("entityId") Long entityId,
                          @Param("fromTs") LocalDateTime fromTs,
                          @Param("toTs") LocalDateTime toTs,
                          @Param("search") String search,
                          Pageable pageable);

    /** Per-record history tab (customer, quotation). Always scoped to one partner for partner roles. */
    @Query("select a from AuditLog a where a.deleted = false "
            + "and (:partnerId is null or a.channelPartnerId = :partnerId) "
            + "and a.entityType = :entityType and a.entityId = :entityId "
            + "order by a.createdAt desc")
    List<AuditLog> findHistory(@Param("partnerId") Long partnerId,
                               @Param("entityType") String entityType,
                               @Param("entityId") Long entityId,
                               Pageable pageable);
}
