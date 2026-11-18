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

/**
 * Read + insert only. The inherited delete and deleteAll methods exist on JpaRepository, but audit_log is protected
 * by @Immutable, by DB triggers and by the grants of the application's DB user, and AuditImmutabilityTest fails
 * the build if anything in the code base calls them.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** tenantId null = every tenant (platform audit screen only; AuditAccessGuard has already run). */
    @Query("select a from AuditLog a where 1 = 1 "
            + "and (:tenantId is null or a.tenantId = :tenantId) "
            + "and (:userId is null or a.actorUserId = :userId) "
            + "and (:module is null or a.module = :module) "
            + "and (:action is null or a.action = :action) "
            + "and (:entityType is null or a.entityType = :entityType) "
            + "and (:entityId is null or a.entityId = :entityId) "
            + "and a.createdAt >= :fromTs and a.createdAt < :toTs "
            + "and (:search is null or lower(a.entityLabel) like lower(concat('%', :search, '%')) "
            + "     or lower(a.actorName) like lower(concat('%', :search, '%')) "
            + "     or lower(a.entityType) like lower(concat('%', :search, '%')))")
    Page<AuditLog> search(@Param("tenantId") Long tenantId,
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
    @Query("select a from AuditLog a where (:tenantId is null or a.tenantId = :tenantId) "
            + "and (:hideAdmin = false or a.actorRole is null or a.actorRole <> 'SUPER_ADMIN') "
            + "and a.entityType = :entityType and a.entityId = :entityId "
            + "order by a.createdAt desc, a.id desc")
    List<AuditLog> findHistory(@Param("tenantId") Long tenantId,
                               @Param("hideAdmin") boolean hideAdmin,
                               @Param("entityType") String entityType,
                               @Param("entityId") Long entityId,
                               Pageable pageable);

    /** Modules that actually appear in the audit log (feeds the module filter on the Audit Log screen). */
    @Query("select distinct a.module from AuditLog a order by a.module")
    List<String> distinctModules();
}