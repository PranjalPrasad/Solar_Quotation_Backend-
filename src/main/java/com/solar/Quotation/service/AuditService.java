package com.solar.Quotation.service;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.dto.requestdto.AuditFilterRequestDto;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.util.DiffUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit trail.
 *
 * WRITING. Two families, chosen by what a lost audit row means:
 *
 *  1. record(...) / recordAs(...)   - business changes (create, update, delete, status, approval, permission, password
 *     reset, export). They join the CALLER'S transaction (Propagation.MANDATORY: calling them outside a transaction
 *     is a programming error and fails at once). The audit row and the business change commit or roll back together.
 *     If the audit row cannot be written the exception propagates, the business change is rolled back and the
 *     request fails with 500 AUDIT_WRITE_FAILED (fail-closed: an unaudited change is not allowed to exist).
 *     Pass the raw before/after snapshots: masking, the diff and the JSON columns are produced here.
 *
 *  2. recordEvent(...)              - authentication events and system runs (login, logout, failed login, lockout,
 *     token reuse, scheduler runs). Written in their OWN transaction; a failure is logged at ERROR with the
 *     "[AUDIT]" prefix and swallowed (alert-only). Reason: there is no business change to protect, and failing
 *     closed would let an audit outage switch off login throttling and lock everyone out.
 *
 * Neither family ever logs snapshot contents.
 */
public interface AuditService {

    // ------------------------------------------------------------------ writes: same transaction, fail-closed

    /** Actor = the user in TenantContext. before = null for CREATE, after = null for DELETE. */
    void record(AuditAction action, String module, String entityType, Long entityId, String entityLabel,
                Long tenantId, Map<String, Object> before, Map<String, Object> after);

    /** Same as record, with an explicit actor (password reset by link: TenantContext is not set). */
    void recordAs(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                  String entityLabel, Long tenantId, Map<String, Object> before, Map<String, Object> after);

    // ------------------------------------------------------------------ writes: own transaction, alert-only

    void recordEvent(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                     String entityLabel, Long tenantId, Map<String, Object> before, Map<String, Object> after);

    /** Event with a plain note ("ip=...", "reason=..."). The note is stored as new_data {"details": ...}. */
    default void recordEvent(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                             String entityLabel, Long tenantId, String details) {
        Map<String, Object> after = null;
        if (details != null && !details.isBlank()) {
            after = new LinkedHashMap<>();
            after.put("details", details);
        }
        recordEvent(actor, action, module, entityType, entityId, entityLabel, tenantId, null, after);
    }

    // ------------------------------------------------------------------ legacy (changes list only, no snapshots)

    /** @deprecated pass before/after so previous_data / new_data are stored. Kept for old call sites. */
    @Deprecated
    void record(AuditAction action, String module, String entityType, Long entityId,
                String entityLabel, Long partnerId, List<Map<String, String>> changes);

    /** @deprecated see above. */
    @Deprecated
    void recordAs(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                  String entityLabel, Long partnerId, List<Map<String, String>> changes);

    /** @deprecated use recordEvent(..., String details). */
    @Deprecated
    default void recordAs(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                          String entityLabel, Long partnerId, String details) {
        recordAs(actor, action, module, entityType, entityId, entityLabel, partnerId, noteChanges(details));
    }

    /** Builds the changes list through DiffUtil so the stored JSON has the same shape as every other audit row. */
    static List<Map<String, String>> noteChanges(String details) {
        if (details == null || details.isBlank()) return null;
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("details", details);
        return DiffUtil.diff(null, after);
    }

    // ------------------------------------------------------------------ reads

    /** Audit screen. Platform staff holding AUDIT:VIEW only (403 otherwise). List rows carry no snapshots. */
    PageResponse<AuditLogResponseDto> search(AuditFilterRequestDto filter);

    /** One entry with previous_data / new_data for the details drawer. AUDIT:VIEW. */
    AuditLogResponseDto get(Long id);

    /** CSV bytes of the same filter (newest 5000 rows). AUDIT:EXPORT. The export itself is audited. */
    byte[] exportCsv(AuditFilterRequestDto filter);

    /** Per-record history. Partner users get only action, time and actor name. */
    List<AuditLogResponseDto> entityHistory(String entityType, Long entityId);

    /** Values for the filter dropdowns, from the database and the AuditAction enum. Keys: modules, actions. AUDIT:VIEW. */
    Map<String, List<String>> filterOptions();
}