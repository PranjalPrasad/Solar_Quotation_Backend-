package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.dto.requestdto.AuditFilterRequestDto;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.entity.AuditLog;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.AuditLogRepository;
import com.solar.Quotation.security.AuditAccessGuard;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.PlatformTenant;
import com.solar.Quotation.util.AuditMasker;
import com.solar.Quotation.util.SpreadsheetSafe;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "module", "action", "actorName", "entityType");
    private static final LocalDateTime MIN_TS = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_TS = LocalDateTime.of(2100, 1, 1, 0, 0);
    private static final int EXPORT_LIMIT = 5000;

    private final AuditLogRepository repository;
    private final TenantResolver tenantResolver;
    private final PartnerRefProvider partnerRefProvider;
    private final PlatformTenant platformTenant;
    private final AuditAccessGuard accessGuard;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate ownTransaction;

    public AuditServiceImpl(AuditLogRepository repository, TenantResolver tenantResolver,
                            PartnerRefProvider partnerRefProvider, PlatformTenant platformTenant,
                            AuditAccessGuard accessGuard, ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.tenantResolver = tenantResolver;
        this.partnerRefProvider = partnerRefProvider;
        this.platformTenant = platformTenant;
        this.accessGuard = accessGuard;
        this.objectMapper = objectMapper;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // =====================================================================================================
    // Writes
    // =====================================================================================================

    /** Same transaction as the business change. MANDATORY: no transaction = programming error, fails at once. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String module, String entityType, Long entityId, String entityLabel,
                       Long tenantId, Map<String, Object> before, Map<String, Object> after) {
        writeOrFail(TenantContext.get(), action, module, entityType, entityId, entityLabel, tenantId, before, after);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                         String entityLabel, Long tenantId, Map<String, Object> before, Map<String, Object> after) {
        writeOrFail(actor, action, module, entityType, entityId, entityLabel, tenantId, before, after);
    }

    /**
     * Own transaction, failures logged and swallowed. Deliberately NOT @Transactional: a failed INSERT marks its
     * transaction rollback-only, and swallowing the exception inside the transactional method would still end in
     * UnexpectedRollbackException for the caller. The try/catch has to sit OUTSIDE the transaction boundary.
     */
    @Override
    public void recordEvent(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                            String entityLabel, Long tenantId, Map<String, Object> before, Map<String, Object> after) {
        try {
            ownTransaction.executeWithoutResult(status ->
                    save(actor, action, module, entityType, entityId, entityLabel, tenantId, before, after));
        } catch (RuntimeException ex) {
            logWriteFailure("alert-only", actor, action, module, entityType, entityId, tenantId, ex);
        }
    }

    // ---- legacy: changes list only (no snapshots) --------------------------------------------------------

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    @Deprecated
    public void record(AuditAction action, String module, String entityType, Long entityId, String entityLabel,
                       Long partnerId, List<Map<String, String>> changes) {
        recordAs(TenantContext.get(), action, module, entityType, entityId, entityLabel, partnerId, changes);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    @Deprecated
    public void recordAs(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                         String entityLabel, Long partnerId, List<Map<String, String>> changes) {
        try {
            AuditLog row = base(actor, action, module, entityType, entityId, entityLabel, partnerId)
                    .changesJson(toJson(changes)).build();
            repository.save(row);
        } catch (RuntimeException ex) {
            logWriteFailure("fail-closed", actor, action, module, entityType, entityId, partnerId, ex);
            throw writeFailed();
        }
    }

    // ---- internals ---------------------------------------------------------------------------------------

    private void writeOrFail(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                             String entityLabel, Long tenantId, Map<String, Object> before, Map<String, Object> after) {
        try {
            save(actor, action, module, entityType, entityId, entityLabel, tenantId, before, after);
        } catch (RuntimeException ex) {
            logWriteFailure("fail-closed", actor, action, module, entityType, entityId, tenantId, ex);
            throw writeFailed();     // propagates through the caller's @Transactional: the business change rolls back too
        }
    }

    private void save(CurrentUser actor, AuditAction action, String module, String entityType, Long entityId,
                      String entityLabel, Long tenantId, Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> maskedBefore = AuditMasker.mask(before);
        Map<String, Object> maskedAfter = AuditMasker.mask(after);
        List<Map<String, String>> changes = (before == null && after == null)
                ? null : AuditMasker.changes(before, after);
        AuditLog row = base(actor, action, module, entityType, entityId, entityLabel, tenantId)
                .changesJson(toJson(changes))
                .previousData(toJson(maskedBefore))
                .newData(toJson(maskedAfter))
                .build();
        repository.save(row);      // IDENTITY id: the INSERT runs now, so a failure surfaces here, inside the try/catch
    }

    private AuditLog.Builder base(CurrentUser actor, AuditAction action, String module, String entityType,
                                  Long entityId, String entityLabel, Long tenantId) {
        AuditLog.Builder b = AuditLog.builder()
                .tenantId(tenantId != null ? tenantId : platformTenant.id())   // events without a tenant belong to PLATFORM
                .module(cut(module, 40))
                .entityType(cut(entityType, 60))
                .entityId(entityId)
                .entityLabel(cut(entityLabel, 150))
                .action(action);
        if (actor != null) {
            b.actorUserId(actor.getUserId()).actorName(cut(actor.getName(), 150)).actorRole(cut(actor.getRoleCode(), 60))
                    .actorTenantId(actor.getPartnerId());
        } else {
            b.actorName("SYSTEM");
        }
        HttpServletRequest req = currentRequest();
        if (req != null) {
            b.ipAddress(cut(clientIp(req), 64));
            b.userAgent(cut(req.getHeader("User-Agent"), 255));
        }
        return b;
    }

    /**
     * [AUDIT] ERROR line. Contains ids, the action and the exception CLASSES only: never the snapshot, never the
     * exception message (a database message can quote column data). The stack trace is available at DEBUG.
     * OPERATIONS: alert on this line. mode=fail-closed means the user's request was rejected and rolled back;
     * mode=alert-only means the request went through WITHOUT its audit row and someone must look.
     */
    private void logWriteFailure(String mode, CurrentUser actor, AuditAction action, String module, String entityType,
                                 Long entityId, Long tenantId, RuntimeException ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        log.error("[AUDIT] failed to write audit row mode={} action={} module={} entityType={} entityId={} tenantId={} actorUserId={} error={} root={}",
                mode, action, module, entityType, entityId, tenantId, actor == null ? null : actor.getUserId(),
                ex.getClass().getSimpleName(), root.getClass().getSimpleName());
        if (log.isDebugEnabled()) log.debug("[AUDIT] write failure detail", ex);
    }

    private BusinessException writeFailed() {
        return new BusinessException("AUDIT_WRITE_FAILED",
                "The action was not completed because it could not be recorded in the audit log. Please try again.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // =====================================================================================================
    // Reads
    // =====================================================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponseDto> search(AuditFilterRequestDto f) {
        accessGuard.require(AuditAccessGuard.VIEW);
        Pageable pageable = f.toPageable("createdAt,desc", SORT_FIELDS);
        // tie-break on id so paging never repeats or skips rows that share a timestamp
        pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id")));
        Page<AuditLog> page = runSearch(f, pageable);
        Map<Long, PartnerRefResponseDto> partners = partnerRefProvider.refs(
                new HashSet<>(page.getContent().stream().map(AuditLog::getTenantId).toList()));
        return PageResponse.of(page.map(a -> toResponse(a, true, false, partners)));
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogResponseDto get(Long id) {
        accessGuard.require(AuditAccessGuard.VIEW);
        AuditLog a = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Audit entry not found"));
        return toResponse(a, true, true, partnerRefProvider.refs(java.util.List.of(a.getTenantId())));
    }

    /** Not read-only: the export writes its own audit row (EXPORT) in this transaction. */
    @Override
    @Transactional
    public byte[] exportCsv(AuditFilterRequestDto f) {
        accessGuard.require(AuditAccessGuard.EXPORT);
        Pageable pageable = PageRequest.of(0, EXPORT_LIMIT,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        List<AuditLog> rows = runSearch(f, pageable).getContent();
        Map<Long, PartnerRefResponseDto> partners = partnerRefProvider.refs(
                new HashSet<>(rows.stream().map(AuditLog::getTenantId).toList()));

        StringBuilder sb = new StringBuilder(
                "Time (UTC),Tenant ID,Partner,Actor tenant ID,Actor,Role,Module,Action,Entity,Entity ID,Label,Changed fields,IP\n");
        for (AuditLog a : rows) {
            PartnerRefResponseDto p = partners.get(a.getTenantId());
            sb.append(csv(String.valueOf(a.getCreatedAt()))).append(',')
                    .append(csv(String.valueOf(a.getTenantId()))).append(',')
                    .append(csv(p == null ? "" : p.getName())).append(',')
                    .append(csv(a.getActorTenantId() == null ? "" : String.valueOf(a.getActorTenantId()))).append(',')
                    .append(csv(a.getActorName())).append(',')
                    .append(csv(a.getActorRole())).append(',')
                    .append(csv(a.getModule())).append(',')
                    .append(csv(String.valueOf(a.getAction()))).append(',')
                    .append(csv(a.getEntityType())).append(',')
                    .append(csv(a.getEntityId() == null ? "" : String.valueOf(a.getEntityId()))).append(',')
                    .append(csv(a.getEntityLabel())).append(',')
                    .append(csv(changedFieldNames(a.getChangesJson()))).append(',')
                    .append(csv(a.getIpAddress())).append('\n');
        }

        Map<String, Object> after = new LinkedHashMap<>();       // what was exported (filters and count), never the data
        after.put("rows", rows.size());
        after.put("partner", f.getPartner() != null ? f.getPartner() : f.getPartnerId());
        after.put("userId", f.getUserId());
        after.put("module", f.getModule());
        after.put("action", f.getAction());
        after.put("entityType", f.getEntityType());
        after.put("entityId", f.getEntityId());
        after.put("from", f.getFrom() == null ? null : f.getFrom().toString());
        after.put("to", f.getTo() == null ? null : f.getTo().toString());
        after.put("search", f.getSearchOrNull());
        record(AuditAction.EXPORT, "AUDIT", "AuditLog", null, "audit-log.csv", platformTenant.id(), null, after);
        log.info("[AUDIT] export rows={} by userId={}", rows.size(),
                TenantContext.get() == null ? null : TenantContext.get().getUserId());
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> entityHistory(String entityType, Long entityId) {
        // GET only: own tenant, or the partner named by ?viewPartnerId= for Super Admin (read only)
        Long tenantId = tenantResolver.effectiveReadPartnerId();
        CurrentUser u = TenantContext.get();
        boolean full = u != null && u.isSuperAdmin();
        List<AuditLog> rows = repository.findHistory(tenantId, !full, entityType, entityId, PageRequest.of(0, 200));
        Map<Long, PartnerRefResponseDto> partners = partnerRefProvider.refs(
                new HashSet<>(rows.stream().map(AuditLog::getTenantId).toList()));
        return rows.stream().map(a -> toResponse(a, full, false, partners)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<String>> filterOptions() {
        accessGuard.require(AuditAccessGuard.VIEW);
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put("modules", repository.distinctModules());
        out.put("actions", Arrays.stream(AuditAction.values()).map(Enum::name).toList());
        return out;
    }

    // =====================================================================================================
    // helpers
    // =====================================================================================================

    private Page<AuditLog> runSearch(AuditFilterRequestDto f, Pageable pageable) {
        AuditAction action = null;
        if (f.getAction() != null && !f.getAction().isBlank()) {
            try {
                action = AuditAction.valueOf(f.getAction().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("VALIDATION_ERROR", "Unknown action");
            }
        }
        if (f.getFrom() != null && f.getTo() != null && f.getFrom().isAfter(f.getTo())) {
            throw new BusinessException("VALIDATION_ERROR", "'from' must be on or before 'to'");
        }
        LocalDateTime from = f.getFrom() == null ? MIN_TS : f.getFrom().atStartOfDay();
        LocalDateTime to = f.getTo() == null ? MAX_TS : f.getTo().plusDays(1).atStartOfDay();
        String module = blankToNull(f.getModule());
        String entityType = blankToNull(f.getEntityType());
        return repository.search(resolveTenantFilter(f), f.getUserId(), module == null ? null : module.toUpperCase(),
                action, entityType, f.getEntityId(), from, to, f.getSearchOrNull(), pageable);
    }

    /** "PLATFORM" or a numeric id in ?partner=, else ?partnerId=, else null = all tenants. */
    private Long resolveTenantFilter(AuditFilterRequestDto f) {
        String key = blankToNull(f.getPartner());
        if (key == null) return f.getPartnerId();
        if ("PLATFORM".equalsIgnoreCase(key)) return platformTenant.id();
        try {
            return Long.valueOf(key);
        } catch (NumberFormatException ex) {
            throw new BusinessException("VALIDATION_ERROR", "partner must be PLATFORM or a partner id");
        }
    }

    private AuditLogResponseDto toResponse(AuditLog a, boolean full, boolean snapshots,
                                           Map<Long, PartnerRefResponseDto> partners) {
        AuditLogResponseDto d = new AuditLogResponseDto();
        d.setId(a.getId());
        d.setCreatedAt(a.getCreatedAt());
        d.setActorName(a.getActorName());
        d.setAction(a.getAction().name());
        d.setModule(a.getModule());
        d.setEntityType(a.getEntityType());
        d.setEntityId(a.getEntityId());
        d.setEntityLabel(a.getEntityLabel());
        d.setPartner(partners.get(a.getTenantId()));
        if (full) {
            d.setTenantId(a.getTenantId());
            d.setActorTenantId(a.getActorTenantId());
            d.setActorUserId(a.getActorUserId());
            d.setActorRole(a.getActorRole());
            d.setIpAddress(a.getIpAddress());
            d.setUserAgent(a.getUserAgent());
            d.setChanges(parse(a.getChangesJson()));
            if (snapshots) {
                d.setPreviousData(parse(a.getPreviousData()));
                d.setNewData(parse(a.getNewData()));
            }
        }
        return d;
    }

    private String changedFieldNames(String changesJson) {
        Object parsed = parse(changesJson);
        if (!(parsed instanceof List<?> list)) return "";
        List<String> names = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m && m.get("field") != null) names.add(String.valueOf(m.get("field")));
        }
        return String.join("; ", names);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        if (value instanceof List<?> l && l.isEmpty()) return null;
        // a serialisation failure must not be swallowed: an audit row without its snapshot is a silent hole
        return objectMapper.writeValueAsString(value);
    }

    private Object parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private String csv(String v) {
        if (v == null) return "";
        return "\"" + SpreadsheetSafe.text(v).replace("\"", "\"\"") + "\"";
    }
}