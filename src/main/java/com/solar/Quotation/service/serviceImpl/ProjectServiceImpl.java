package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.CustomerType;
import com.solar.Quotation.Enum.ProjectStatus;
import com.solar.Quotation.dto.requestdto.ProjectFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectStatusRequestDto;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.CustomerRefResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.dto.responsedto.ProjectResponseDto;
import com.solar.Quotation.dto.responsedto.ProjectSummaryResponseDto;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.Project;
import com.solar.Quotation.entity.Quotation;
import com.solar.Quotation.exception.ApiException;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.ProjectRepository;
import com.solar.Quotation.repository.QuotationRepository;
import com.solar.Quotation.security.Actors;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.NotificationPublisher;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.ProjectService;
import com.solar.Quotation.util.AuditInfoMapper;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.DiffUtil;
import com.solar.Quotation.util.PartnerGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Tenant rules (same as Customers / Products; Super Admin works in the PLATFORM tenant like any other partner):
 *   READS   list / summary / get / history -> tenantResolver.effectiveReadPartnerId(): own tenant, or the partner named
 *           by ?viewPartnerId= for data scope ALL on a GET. A partnerId in a filter is ignored (WARN).
 *   WRITES  create / update / status / delete -> always the caller's own tenant. The record is loaded by id and refused
 *           with 404 (+ WARN) when it belongs to another tenant (assertWritable). A project is created for a customer
 *           of the caller's own tenant only, so a project and its customer always share one partner.
 *   OWNER   data scope OWN (tenantResolver.ownerFilter()) is applied on every path.
 *
 * Status: only the moves in ProjectStatus.allowedNext() are accepted, from PATCH /status AND from the status field
 * of PUT (which cannot be used to bypass the rules). Every real change writes a STATUS_CHANGE audit row with the
 * previous and new status, and queues a notification carrying the PROJECT's partnerId.
 *
 * Audit: create / update / status change / delete write an audit row with previous and new values (DiffUtil), under
 * the record's own partnerId. Logging: [PROJECT] INFO for writes, WARN for invalid transitions, validation and
 * cross-tenant attempts, ERROR with stack for anything unexpected in a write. Only ids and codes are logged: never
 * project names, locations, remarks or customer data.
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "name", "code", "capacityKw", "status");
    /**
     * Used only when the form leaves estimatedGeneration empty: capacity x units per kW per day x 30 days.
     * TODO: read the 4 from the SAVINGS_CONFIG setting once the settings module exists.
     */
    private static final BigDecimal DEFAULT_UNITS_PER_KW_PER_DAY = new BigDecimal("4");
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");

    private final ProjectRepository projectRepository;
    private final CustomerRepository customerRepository;
    private final TenantResolver tenantResolver;
    private final PartnerRefProvider partnerRefProvider;
    private final AuditInfoMapper auditInfoMapper;
    private final AuditService auditService;
    private final CodeGenerator codeGenerator;
    private final QuotationRepository quotationRepository;
    private final PartnerGuard partnerGuard;
    private final NotificationPublisher notificationPublisher;

    public ProjectServiceImpl(ProjectRepository projectRepository, CustomerRepository customerRepository,
                              TenantResolver tenantResolver, PartnerRefProvider partnerRefProvider,
                              AuditInfoMapper auditInfoMapper, AuditService auditService, CodeGenerator codeGenerator,
                              QuotationRepository quotationRepository, PartnerGuard partnerGuard,
                              NotificationPublisher notificationPublisher) {
        this.projectRepository = projectRepository;
        this.customerRepository = customerRepository;
        this.tenantResolver = tenantResolver;
        this.partnerRefProvider = partnerRefProvider;
        this.auditInfoMapper = auditInfoMapper;
        this.auditService = auditService;
        this.codeGenerator = codeGenerator;
        this.quotationRepository = quotationRepository;
        this.partnerGuard = partnerGuard;
        this.notificationPublisher = notificationPublisher;
    }

    // ---------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponseDto> list(ProjectFilterRequestDto f) {
        warnIfClientPartner(f.getPartnerId());
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        Long ownerId = tenantResolver.ownerFilter();
        Page<Project> page = projectRepository.search(partnerId, ownerId, parseType(f.getType()),
                parseStatus(f.getStatus()), f.getCustomerId(), f.getSearchOrNull(),
                f.toPageable("createdAt,desc", SORT_FIELDS));
        Lookup lookup = lookup(page.getContent());
        log.debug("[PROJECT] list partnerId={} rows={} total={}", partnerId, page.getNumberOfElements(), page.getTotalElements());
        return PageResponse.of(page.map(p -> toResponse(p, lookup)));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectSummaryResponseDto summary() {
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        Long ownerId = tenantResolver.ownerFilter();
        ProjectSummaryResponseDto s = new ProjectSummaryResponseDto();
        s.setTotal(projectRepository.countFiltered(partnerId, ownerId, null, null));
        s.setResidential(projectRepository.countFiltered(partnerId, ownerId, CustomerType.RESIDENTIAL, null));
        s.setCommercial(projectRepository.countFiltered(partnerId, ownerId, CustomerType.COMMERCIAL, null));
        s.setIndustrial(projectRepository.countFiltered(partnerId, ownerId, CustomerType.INDUSTRIAL, null));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ProjectStatus st : ProjectStatus.values()) {
            byStatus.put(st.name(), projectRepository.countFiltered(partnerId, ownerId, null, st));
        }
        s.setByStatus(byStatus);
        return s;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponseDto get(Long id) {
        return toSingleResponse(findForRead(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> history(Long id) {
        Project p = findForRead(id);                     // 404 if it is not visible to the caller
        List<AuditLogResponseDto> rows = new ArrayList<>(auditService.entityHistory("Project", p.getId()));
        for (Quotation q : quotationRepository.findLinkedToProjects(List.of(p.getId()))) {
            rows.addAll(auditService.entityHistory("Quotation", q.getId()));   // approval / status trail of the quotation
        }
        rows.sort(Comparator.comparing(AuditLogResponseDto::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    // ---------------------------------------------------------------- write

    @Override
    @Transactional
    public ProjectResponseDto create(ProjectRequestDto req) {
        return guarded("create", () -> doCreate(req));
    }

    private ProjectResponseDto doCreate(ProjectRequestDto req) {
        // always the caller's own tenant; the customer must be in that tenant too (and visible under data scope OWN)
        Long partnerId = tenantResolver.requirePartnerIdForCreate(null);
        partnerGuard.assertUsable(partnerId);
        Customer customer = customerRepository.findOne(req.getCustomerId(), partnerId, tenantResolver.ownerFilter())
                .orElseThrow(() -> invalid("VALIDATION_ERROR", "Customer not found",
                        "create refused, customer not found in own tenant customerId=" + req.getCustomerId()
                                + " partnerId=" + partnerId));

        Project p = new Project();
        p.setChannelPartnerId(partnerId);
        p.setCode(codeGenerator.next("PRJ", partnerId));
        p.setCustomerId(customer.getId());
        applyFields(p, req);
        p.setStatus(ProjectStatus.PLANNING);                 // a project always starts in PLANNING
        Project saved = projectRepository.save(p);

        auditService.record(AuditAction.CREATE, "PROJECT", "Project", saved.getId(), saved.getCode(),
                partnerId, null, snapshot(saved));
        log.info("[PROJECT] created id={} code={} partnerId={} type={} {}", saved.getId(), saved.getCode(), partnerId,
                saved.getType(), Actors.describe());
        return toSingleResponse(saved);
    }

    @Override
    @Transactional
    public ProjectResponseDto update(Long id, ProjectRequestDto req) {
        return guarded("update", () -> doUpdate(id, req));
    }

    private ProjectResponseDto doUpdate(Long id, ProjectRequestDto req) {
        Project p = findForWrite(id);

        // everything is checked BEFORE the first change, so a refused request leaves the project untouched
        ProjectStatus previous = p.getStatus();
        ProjectStatus requested = parseStatus(req.getStatus());
        boolean statusChanges = requested != null && requested != previous;
        if (statusChanges) {
            assertTransition(p, previous, requested);
        }
        Customer newCustomer = null;
        if (!p.getCustomerId().equals(req.getCustomerId())) {
            // the new customer must belong to the SAME partner as the project
            newCustomer = customerRepository.findOne(req.getCustomerId(), p.getChannelPartnerId(),
                            tenantResolver.ownerFilter())
                    .orElseThrow(() -> invalid("VALIDATION_ERROR", "Customer not found",
                            "update refused, customer not found in own tenant id=" + id
                                    + " customerId=" + req.getCustomerId() + " partnerId=" + p.getChannelPartnerId()));
        }

        Map<String, Object> before = snapshot(p);            // taken BEFORE any field is changed
        if (newCustomer != null) {
            p.setCustomerId(newCustomer.getId());
        }
        applyFields(p, req);
        if (statusChanges) {
            p.setStatus(requested);
        }
        Project saved = projectRepository.save(p);
        Map<String, Object> after = snapshot(saved);

        // field changes and the status change are separate audit rows, so the history reads "updated" / "status A -> B"
        List<Map<String, String>> fieldChanges = DiffUtil.diff(without(before, "status"), without(after, "status"));
        if (!fieldChanges.isEmpty()) {
            auditService.record(AuditAction.UPDATE, "PROJECT", "Project", saved.getId(), saved.getCode(),
                    saved.getChannelPartnerId(), without(before, "status"), without(after, "status"));
            log.info("[PROJECT] updated id={} code={} partnerId={} changedFields={} {}", saved.getId(), saved.getCode(),
                    saved.getChannelPartnerId(), fieldChanges.size(), Actors.describe());
        } else if (!statusChanges) {
            log.info("[PROJECT] update saved, nothing changed id={} code={} partnerId={}", saved.getId(), saved.getCode(),
                    saved.getChannelPartnerId());
        }
        if (statusChanges) {
            recordStatusChange(saved, previous, before, after);
        }
        return toSingleResponse(saved);
    }

    @Override
    @Transactional
    public ProjectResponseDto updateStatus(Long id, ProjectStatusRequestDto req) {
        return guarded("status change", () -> doUpdateStatus(id, req));
    }

    private ProjectResponseDto doUpdateStatus(Long id, ProjectStatusRequestDto req) {
        Project p = findForWrite(id);
        ProjectStatus previous = p.getStatus();
        ProjectStatus next = ProjectStatus.valueOf(req.getStatus());
        if (previous == next) {
            log.debug("[PROJECT] status unchanged id={} status={}", id, next);
            return toSingleResponse(p);
        }
        assertTransition(p, previous, next);

        Map<String, Object> before = snapshot(p);
        p.setStatus(next);
        Project saved = projectRepository.save(p);
        recordStatusChange(saved, previous, before, snapshot(saved));
        return toSingleResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        guarded("delete", () -> {
            doDelete(id);
            return null;
        });
    }

    private void doDelete(Long id) {
        Project p = findForWrite(id);
        Map<String, Object> before = snapshot(p);
        String code = p.getCode();
        Long partnerId = p.getChannelPartnerId();
        p.setDeleted(true);
        projectRepository.save(p);
        auditService.record(AuditAction.DELETE, "PROJECT", "Project", id, code, partnerId, before, null);
        log.info("[PROJECT] deleted id={} code={} partnerId={} {}", id, code, partnerId, Actors.describe());
    }

    // ---------------------------------------------------------------- status helpers

    /** WARN + 409 for a move that is not in ProjectStatus.allowedNext(). Nothing has been changed when this throws. */
    private void assertTransition(Project p, ProjectStatus from, ProjectStatus to) {
        if (from.canMoveTo(to)) {
            return;
        }
        log.warn("[PROJECT] invalid status transition refused id={} code={} partnerId={} {} -> {} {}",
                p.getId(), p.getCode(), p.getChannelPartnerId(), from, to, Actors.describe());
        throw new BusinessException("INVALID_TRANSITION",
                "A project cannot move from " + pretty(from) + " to " + pretty(to), HttpStatus.CONFLICT);
    }

    /** Audit row (previous + new status, under the project's partner), INFO log, and the notification. */
    private void recordStatusChange(Project saved, ProjectStatus previous,
                                    Map<String, Object> before, Map<String, Object> after) {
        auditService.record(AuditAction.STATUS_CHANGE, "PROJECT", "Project", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(),
                only(before, "status"), only(after, "status"));
        log.info("[PROJECT] status changed id={} code={} partnerId={} {} -> {} {}", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), previous, saved.getStatus(), Actors.describe());
        // recipients and partnerId come from the PROJECT (its tenant), never from the caller's request
        notificationPublisher.projectStatusChanged(saved, previous);
    }

    // ---------------------------------------------------------------- tenant helpers

    /** GET: own tenant, or the viewed partner for Super Admin; data scope OWN applies. */
    private Project findForRead(Long id) {
        return projectRepository.findOne(id, tenantResolver.effectiveReadPartnerId(), tenantResolver.ownerFilter())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }

    /**
     * Writes: loads by id (data scope OWN still applies), then refuses anything outside the own tenant.
     * A project of another tenant looks exactly like a missing one (404) and TenantResolver logs it at WARN.
     */
    private Project findForWrite(Long id) {
        Project p = projectRepository.findOne(id, null, tenantResolver.ownerFilter())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        tenantResolver.assertWritable(p.getChannelPartnerId(), "Project", id);
        return p;
    }

    private void warnIfClientPartner(Long requested) {
        if (requested != null) {
            log.warn("[PROJECT] client partnerId ignored on read requestedPartnerId={}", requested);
        }
    }

    /** Logs a validation problem at WARN and returns the exception to throw. */
    private BusinessException invalid(String code, String message, String logDetail) {
        log.warn("[PROJECT] validation: {}", logDetail);
        return new BusinessException(code, message);
    }

    /** Runs a write; anything unexpected is logged at ERROR with the stack, then rethrown to the API error handler. */
    private <T> T guarded(String operation, Supplier<T> body) {
        try {
            return body.get();
        } catch (BusinessException | ResourceNotFoundException | ForbiddenException | ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[PROJECT] {} failed unexpectedly", operation, e);
            throw e;
        }
    }

    /** Strips control characters from user text before it goes into a log line (log forging). */
    private static String clean(String s) {
        if (s == null) return "-";
        String t = s.replaceAll("[\\r\\n\\t]", " ");
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    // ---------------------------------------------------------------- field helpers

    /** Everything except the status: the status only changes through the validated transition path. */
    private void applyFields(Project p, ProjectRequestDto req) {
        p.setName(req.getName().trim());
        p.setType(CustomerType.valueOf(req.getType()));
        p.setCapacityKw(req.getCapacityKw().setScale(2, RoundingMode.HALF_UP));
        p.setRoofType(blankToNull(req.getRoofType()));
        p.setMonthlyConsumption(req.getMonthlyConsumption());
        p.setLocation(blankToNull(req.getLocation()));
        p.setRoofArea(req.getRoofArea());
        p.setEstimatedGeneration(req.getEstimatedGeneration() != null
                ? req.getEstimatedGeneration()
                : p.getCapacityKw().multiply(DEFAULT_UNITS_PER_KW_PER_DAY).multiply(DAYS_PER_MONTH)
                .setScale(2, RoundingMode.HALF_UP));
        p.setInstallationType(blankToNull(req.getInstallationType()));
        p.setRemarks(blankToNull(req.getRemarks()));
    }

    private CustomerType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CustomerType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("VALIDATION_ERROR", "Unknown project type " + value, "unknown project type " + clean(value));
        }
    }

    private ProjectStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ProjectStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("VALIDATION_ERROR", "Unknown project status " + value, "unknown project status " + clean(value));
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String pretty(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private Map<String, Object> snapshot(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("customerId", p.getCustomerId() == null ? null : p.getCustomerId().toString());
        m.put("type", p.getType().name());
        m.put("capacityKw", p.getCapacityKw() == null ? null : p.getCapacityKw().toPlainString());
        m.put("roofType", p.getRoofType());
        m.put("monthlyConsumption", plain(p.getMonthlyConsumption()));
        m.put("location", p.getLocation());
        m.put("roofArea", plain(p.getRoofArea()));
        m.put("estimatedGeneration", plain(p.getEstimatedGeneration()));
        m.put("installationType", p.getInstallationType());
        m.put("status", p.getStatus().name());
        m.put("remarks", p.getRemarks());
        return m;
    }

    private static Map<String, Object> without(Map<String, Object> m, String key) {
        Map<String, Object> out = new LinkedHashMap<>(m);
        out.remove(key);
        return out;
    }

    private static Map<String, Object> only(Map<String, Object> m, String key) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(key, m.get(key));
        return out;
    }

    private String plain(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    // ---------------------------------------------------------------- response mapping

    private static final class Lookup {
        private Map<Long, PartnerRefResponseDto> partners = new HashMap<>();
        private Map<Long, String> names = new HashMap<>();
        private final Map<Long, CustomerRefResponseDto> customers = new HashMap<>();
        private final Map<Long, Quotation> quotations = new HashMap<>();   // projectId -> latest linked quotation
    }

    private Lookup lookup(List<Project> projects) {
        Lookup lk = new Lookup();
        if (projects.isEmpty()) {
            return lk;
        }
        Set<Long> customerIds = new HashSet<>();
        Set<Long> partnerIds = new HashSet<>();
        for (Project p : projects) {
            customerIds.add(p.getCustomerId());
            partnerIds.add(p.getChannelPartnerId());
        }
        lk.partners = partnerRefProvider.refs(partnerIds);
        lk.names = auditInfoMapper.namesFor(projects);
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        for (Quotation q : quotationRepository.findLinkedToProjects(projectIds)) {
            lk.quotations.putIfAbsent(q.getProjectId(), q);   // list is newest first
        }
        for (Customer c : customerRepository.findAllById(customerIds)) {
            if (partnerIds.contains(c.getChannelPartnerId())) {       // never name a customer of another tenant
                lk.customers.put(c.getId(), new CustomerRefResponseDto(c.getId(), c.getCode(), c.getName()));
            }
        }
        return lk;
    }

    private ProjectResponseDto toSingleResponse(Project p) {
        return toResponse(p, lookup(List.of(p)));
    }

    private ProjectResponseDto toResponse(Project p, Lookup lk) {
        ProjectResponseDto d = new ProjectResponseDto();
        d.setId(p.getId());
        d.setCode(p.getCode());
        d.setName(p.getName());
        d.setCustomer(lk.customers.get(p.getCustomerId()));
        d.setType(p.getType().name());
        d.setCapacityKw(p.getCapacityKw());
        d.setRoofType(p.getRoofType());
        d.setMonthlyConsumption(p.getMonthlyConsumption());
        d.setLocation(p.getLocation());
        d.setRoofArea(p.getRoofArea());
        d.setEstimatedGeneration(p.getEstimatedGeneration());
        d.setInstallationType(p.getInstallationType());
        d.setStatus(p.getStatus().name());
        d.setRemarks(p.getRemarks());
        Quotation lq = lk.quotations.get(p.getId());
        if (lq != null) {
            d.setQuotationId(lq.getId());
            d.setQuotationCode(lq.getCode());
            d.setQuotationStatus(lq.getStatus().name());
        }
        d.setPartner(lk.partners.get(p.getChannelPartnerId()));
        d.setAudit(auditInfoMapper.of(p, lk.names));
        return d;
    }
}