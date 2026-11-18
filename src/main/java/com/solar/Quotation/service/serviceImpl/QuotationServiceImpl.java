package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DiscountType;
import com.solar.Quotation.Enum.ProjectStatus;
import com.solar.Quotation.Enum.QuotationStatus;
import com.solar.Quotation.Enum.ReferralStatus;
import com.solar.Quotation.dto.requestdto.QuotationFilterRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationHistoryDto;
import com.solar.Quotation.dto.requestdto.QuotationItemRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationStatusRequestDto;
import com.solar.Quotation.dto.responsedto.BankDetailsResponseDto;
import com.solar.Quotation.dto.responsedto.CustomerRefResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationItemResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationListItemResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationSummaryResponseDto;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.Project;
import com.solar.Quotation.entity.Quotation;
import com.solar.Quotation.entity.QuotationItem;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.ProductRepository;
import com.solar.Quotation.repository.ProjectRepository;
import com.solar.Quotation.repository.QuotationItemRepository;
import com.solar.Quotation.repository.QuotationRepository;
import com.solar.Quotation.security.Actors;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.ApprovalGuard;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.QuotationHistoryService;
import com.solar.Quotation.service.QuotationService;
import com.solar.Quotation.service.SettingsProvider;
import com.solar.Quotation.util.AuditInfoMapper;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.DiffUtil;
import com.solar.Quotation.util.MoneyUtil;
import com.solar.Quotation.util.NumberToWords;
import com.solar.Quotation.util.QuotationCalculator;
import com.solar.Quotation.util.SubsidyCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tenant rules (same as Customers and Products):
 *   READS   list / summary / get / history -> tenantResolver.effectiveReadPartnerId(): own tenant, or the partner named
 *           by ?viewPartnerId= for data scope ALL on a GET. A partnerId in a filter or parameter is ignored (WARN).
 *   WRITES  create / update / status / delete -> always the caller's own tenant. The quotation is loaded by id and
 *           refused with 404 (+ WARN) when it belongs to another tenant (assertWritable).
 *   ITEMS   never loaded by their own id: always through a quotation that already passed the tenant check, with
 *           that quotation's partner id on every query.
 *   HISTORY every row carries channel_partner_id and every read filters by it, after the quotation was tenant-checked.
 *
 * Audit: create / update / delete / status change / approve / reject / convert write an audit row with previous and
 * new values, under the quotation's own partner id; the same writes also append a row to quotation_history.
 * Approval: PATCH status needs APPROVAL:EDIT, never works on another tenant (so the viewPartnerId view is read-only),
 * and the creator cannot approve / reject their own quotation (see ApprovalGuard). Decisions log "[APPROVAL] ..." at INFO.
 * Logging: [QUOTATION] status transitions and writes at INFO, calculation inputs (numbers only, no personal data)
 * at WARN when a calculation fails. Names, mobiles and e-mails are never logged, only ids and codes.
 */
@Service
public class QuotationServiceImpl implements QuotationService {

    private static final Logger log = LoggerFactory.getLogger(QuotationServiceImpl.class);

    private static final Set<String> SORT_FIELDS =
            Set.of("createdAt", "code", "totalAmount", "status", "quoteDate", "systemSizeKw");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository itemRepository;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final ProductRepository productRepository;
    private final TenantResolver tenantResolver;
    private final PartnerRefProvider partnerRefProvider;
    private final AuditInfoMapper auditInfoMapper;
    private final AuditService auditService;
    private final CodeGenerator codeGenerator;
    private final SettingsProvider settingsProvider;
    private final QuotationHistoryService historyService;
    private final ApprovalGuard approvalGuard;

    public QuotationServiceImpl(QuotationRepository quotationRepository, QuotationItemRepository itemRepository,
                                CustomerRepository customerRepository, ProjectRepository projectRepository,
                                ProductRepository productRepository, TenantResolver tenantResolver,
                                PartnerRefProvider partnerRefProvider, AuditInfoMapper auditInfoMapper,
                                AuditService auditService, CodeGenerator codeGenerator,
                                SettingsProvider settingsProvider, QuotationHistoryService historyService,
                                ApprovalGuard approvalGuard) {
        this.quotationRepository = quotationRepository;
        this.itemRepository = itemRepository;
        this.customerRepository = customerRepository;
        this.projectRepository = projectRepository;
        this.productRepository = productRepository;
        this.tenantResolver = tenantResolver;
        this.partnerRefProvider = partnerRefProvider;
        this.auditInfoMapper = auditInfoMapper;
        this.auditService = auditService;
        this.codeGenerator = codeGenerator;
        this.settingsProvider = settingsProvider;
        this.historyService = historyService;
        this.approvalGuard = approvalGuard;
    }

    // ---------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuotationListItemResponseDto> list(QuotationFilterRequestDto f) {
        warnIfClientPartner(f.getPartnerId());
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        Long ownerId = tenantResolver.ownerFilter();
        Page<Quotation> page = quotationRepository.search(partnerId, ownerId, parseStatus(f.getStatus()),
                f.getCustomerId(), f.getReferred(), f.getFromDate(), f.getToDate(), f.getSearchOrNull(),
                f.toPageable("createdAt,desc", SORT_FIELDS));
        ListLookup lookup = listLookup(page.getContent(), partnerId);
        log.debug("[QUOTATION] list partnerId={} rows={} total={}", partnerId, page.getNumberOfElements(), page.getTotalElements());
        return PageResponse.of(page.map(q -> toListItem(q, lookup)));
    }

    @Override
    @Transactional(readOnly = true)
    public QuotationSummaryResponseDto summary(Long requestedPartnerId) {
        warnIfClientPartner(requestedPartnerId);
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        Long ownerId = tenantResolver.ownerFilter();
        QuotationSummaryResponseDto s = new QuotationSummaryResponseDto();
        s.setTotal(quotationRepository.countFiltered(partnerId, ownerId, null));
        s.setPending(quotationRepository.countFiltered(partnerId, ownerId, QuotationStatus.PENDING));
        s.setAccepted(quotationRepository.countFiltered(partnerId, ownerId, QuotationStatus.ACCEPTED));
        s.setRejected(quotationRepository.countFiltered(partnerId, ownerId, QuotationStatus.REJECTED));
        s.setTotalValue(quotationRepository.sumTotal(partnerId, ownerId, null));
        s.setAcceptedValue(quotationRepository.sumTotal(partnerId, ownerId, QuotationStatus.ACCEPTED));
        return s;
    }

    @Override
    @Transactional(readOnly = true)
    public QuotationResponseDto get(Long id) {
        Quotation q = findForRead(id);
        return toResponse(q, itemsOf(q));
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotationHistoryDto> history(Long id) {
        Quotation q = findForRead(id);                   // 404 if it is not visible to the caller
        return historyService.list(q.getId(), q.getChannelPartnerId());
    }

    // ---------------------------------------------------------------- write

    @Override
    @Transactional(readOnly = true)
    public QuotationResponseDto preview(QuotationRequestDto req) {
        Customer customer = findCustomerForCaller(req.getCustomerId());
        Quotation q = new Quotation();
        q.setChannelPartnerId(customer.getChannelPartnerId());
        q.setCustomerId(customer.getId());
        q.setQuoteDate(LocalDate.now(IST));
        q.setStatus(QuotationStatus.PENDING);
        List<QuotationItem> items = applyLogged(q, req, customer, true);
        return toResponse(q, items);
    }

    @Override
    @Transactional
    public QuotationResponseDto create(QuotationRequestDto req) {
        // The customer decides the partner, and findCustomerForCaller only finds customers of the caller's OWN tenant
        // (Super Admin: the PLATFORM tenant), so a quotation is always created under the caller's own partner.
        Customer customer = findCustomerForCaller(req.getCustomerId());
        Long partnerId = customer.getChannelPartnerId();

        Quotation q = new Quotation();
        q.setChannelPartnerId(partnerId);
        q.setCustomerId(customer.getId());
        q.setQuoteDate(LocalDate.now(IST));
        q.setStatus(QuotationStatus.PENDING);
        List<QuotationItem> items = applyLogged(q, req, customer, false);
        q.setCode(codeGenerator.next(codePrefix(partnerId), partnerId));
        Quotation saved = quotationRepository.save(q);

        saveItems(saved, items);

        Map<String, Object> after = snapshot(saved, items);
        auditService.record(AuditAction.CREATE, "QUOTATION", "Quotation", saved.getId(), saved.getCode(),
                partnerId, null, after);
        historyService.recordCreated(partnerId, saved.getId(), saved.getCode(), after);
        log.info("[QUOTATION] created id={} code={} partnerId={} status={} items={} {}",
                saved.getId(), saved.getCode(), partnerId, saved.getStatus(), items.size(), Actors.describe());
        return toResponse(saved, items);
    }

    @Override
    @Transactional
    public QuotationResponseDto update(Long id, QuotationRequestDto req) {
        Quotation q = findForWrite(id);
        if (q.getStatus() != QuotationStatus.PENDING) {
            log.warn("[QUOTATION] update refused, not pending id={} status={} partnerId={}",
                    id, q.getStatus(), q.getChannelPartnerId());
            throw new BusinessException("BUSINESS_RULE", "Only a pending quotation can be edited");
        }
        // previous values are taken BEFORE anything on the quotation (customer included) is changed
        List<QuotationItem> oldItems = itemsOf(q);
        Map<String, Object> before = snapshot(q, oldItems);

        Customer customer;
        if (q.getCustomerId().equals(req.getCustomerId())) {
            customer = customerOf(q).orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "Customer not found"));
        } else {
            // the new customer must belong to the SAME partner as the quotation
            customer = customerRepository.findOne(req.getCustomerId(), q.getChannelPartnerId(),
                            tenantResolver.ownerFilter())
                    .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "Customer not found"));
            q.setCustomerId(customer.getId());
        }

        List<QuotationItem> items = applyLogged(q, req, customer, false);
        Quotation saved = quotationRepository.save(q);

        itemRepository.deleteForQuotation(saved.getId(), saved.getChannelPartnerId());
        saveItems(saved, items);

        Map<String, Object> after = snapshot(saved, items);
        List<Map<String, String>> changes = DiffUtil.diff(before, after);
        if (changes.isEmpty()) {
            log.info("[QUOTATION] update saved, nothing changed id={} code={} partnerId={}",
                    saved.getId(), saved.getCode(), saved.getChannelPartnerId());
        } else {
            auditService.record(AuditAction.UPDATE, "QUOTATION", "Quotation", saved.getId(), saved.getCode(),
                    saved.getChannelPartnerId(), before, after);
            historyService.recordUpdated(saved.getChannelPartnerId(), saved.getId(), saved.getCode(), before, after);
            log.info("[QUOTATION] updated id={} code={} partnerId={} changedFields={} {}",
                    saved.getId(), saved.getCode(), saved.getChannelPartnerId(), changes.size(), Actors.describe());
        }
        return toResponse(saved, items);
    }

    @Override
    @Transactional
    public QuotationResponseDto updateStatus(Long id, QuotationStatusRequestDto req) {
        approvalGuard.assertMayApprove(id);              // APPROVAL:EDIT, before anything is loaded
        Quotation q = findForWrite(id);                  // own tenant only: another partner's quotation is a 404
        List<QuotationItem> items = itemsOf(q);

        QuotationStatus next = QuotationStatus.valueOf(req.getStatus());
        QuotationStatus previous = q.getStatus();
        if (previous == next) {
            log.debug("[QUOTATION] status unchanged id={} status={}", id, next);
            return toResponse(q, items);
        }
        approvalGuard.assertMayDecide(q.getId(), q.getCreatedBy(), q.getChannelPartnerId(), previous, next);
        Map<String, Object> before = snapshot(q, items);

        q.setStatus(next);
        // A referred quotation that gets accepted becomes a payable referral request (Channel Partner module approves it)
        if (next == QuotationStatus.ACCEPTED && q.isReferred() && q.getReferralStatus() == null) {
            q.setReferralStatus(ReferralStatus.PENDING);
        }
        // Approved/accepted quotation without a project yet -> open the project (PLANNING) and link it (= "convert")
        String projectCode = null;
        if ((next == QuotationStatus.ACCEPTED || next == QuotationStatus.APPROVED) && q.getProjectId() == null) {
            Project project = createProjectFromQuotation(q);
            q.setProjectId(project.getId());
            projectCode = project.getCode();
        }
        Quotation saved = quotationRepository.save(q);
        Long partnerId = saved.getChannelPartnerId();
        Map<String, Object> after = snapshot(saved, items);

        // ACCEPTED / APPROVED = approve, REJECTED = reject, anything else (back to pending, expired, paid) = status change
        AuditAction action = next == QuotationStatus.REJECTED ? AuditAction.REJECT
                : (next == QuotationStatus.ACCEPTED || next == QuotationStatus.APPROVED) ? AuditAction.APPROVE
                : AuditAction.STATUS_CHANGE;
        auditService.record(action, "QUOTATION", "Quotation", saved.getId(), saved.getCode(),
                partnerId, before, after);
        historyService.recordStatusChanged(partnerId, saved.getId(), saved.getCode(),
                previous.name(), next.name(), after);

        if (projectCode != null) {
            Map<String, Object> noProject = new LinkedHashMap<>();
            noProject.put("project", null);
            Map<String, Object> withProject = new LinkedHashMap<>();
            withProject.put("project", projectCode);
            auditService.record(AuditAction.CONVERT, "QUOTATION", "Quotation", saved.getId(), saved.getCode(),
                    partnerId, noProject, withProject);
            historyService.recordConverted(partnerId, saved.getId(), saved.getCode(), "project " + projectCode, after);
            log.info("[QUOTATION] converted id={} code={} partnerId={} project={}",
                    saved.getId(), saved.getCode(), partnerId, projectCode);
        }
        log.info("[QUOTATION] status id={} code={} partnerId={} {} -> {} {}",
                saved.getId(), saved.getCode(), partnerId, previous, next, Actors.describe());
        if (action != AuditAction.STATUS_CHANGE) {
            log.info("[APPROVAL] {} quotationId={} code={} partnerId={} {} -> {} {}",
                    action == AuditAction.APPROVE ? "approved" : "rejected",
                    saved.getId(), saved.getCode(), partnerId, previous, next, Actors.describe());
        }
        return toResponse(saved, items);
    }

    /** Builds the project for an accepted quotation from the quotation + customer data (idempotent: caller checks projectId). */
    private Project createProjectFromQuotation(Quotation q) {
        Customer c = customerOf(q)
                .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "Customer not found"));
        Long partnerId = q.getChannelPartnerId();
        BigDecimal kw = q.getSystemSizeKw().setScale(2, RoundingMode.HALF_UP);

        Project p = new Project();
        p.setChannelPartnerId(partnerId);
        p.setCode(codeGenerator.next("PRJ", partnerId));
        p.setCustomerId(c.getId());
        String name = c.getName() + " - " + kw.stripTrailingZeros().toPlainString() + " kW Solar";
        p.setName(name.length() > 200 ? name.substring(0, 200) : name);
        p.setType(c.getType());
        p.setCapacityKw(kw);
        String loc = java.util.stream.Stream.of(c.getCity(), c.getState())
                .filter(x -> x != null && !x.isBlank()).collect(java.util.stream.Collectors.joining(", "));
        p.setLocation(loc.isEmpty() ? null : loc);
        p.setEstimatedGeneration(kw.multiply(new BigDecimal("120")).setScale(2, RoundingMode.HALF_UP)); // 4 units/kW/day x 30
        p.setStatus(ProjectStatus.PLANNING);
        p.setRemarks("Auto-created from quotation " + q.getCode());
        p.setCreatedBy(q.getCreatedBy());   // owner stays the quotation's creator, so sales users still see their project
        Project saved = projectRepository.save(p);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", saved.getName());
        after.put("customerId", c.getId().toString());
        after.put("capacityKw", kw.toPlainString());
        after.put("status", saved.getStatus().name());
        after.put("sourceQuotation", q.getCode());
        auditService.record(AuditAction.CREATE, "PROJECT", "Project", saved.getId(), saved.getCode(),
                partnerId, null, after);
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Quotation q = findForWrite(id);
        if (q.getStatus() == QuotationStatus.ACCEPTED) {
            log.warn("[QUOTATION] delete refused, accepted id={} partnerId={}", id, q.getChannelPartnerId());
            throw new BusinessException("BUSINESS_RULE", "An accepted quotation cannot be deleted");
        }
        String code = q.getCode();
        Long partnerId = q.getChannelPartnerId();
        Map<String, Object> before = snapshot(q, itemsOf(q));     // the previous data stays in the audit row
        q.setDeleted(true);
        quotationRepository.save(q);
        auditService.record(AuditAction.DELETE, "QUOTATION", "Quotation", id, code, partnerId, before, null);
        historyService.recordDeleted(partnerId, id, code, before);
        log.info("[QUOTATION] deleted id={} code={} partnerId={} {}", id, code, partnerId, Actors.describe());
    }

    // ---------------------------------------------------------------- helpers

    /** GET: own tenant, or the viewed partner for Super Admin; data scope OWN applies. */
    private Quotation findForRead(Long id) {
        return quotationRepository.findOne(id, tenantResolver.effectiveReadPartnerId(), tenantResolver.ownerFilter())
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
    }

    /**
     * Writes: loads by id (data scope OWN still applies), then refuses anything outside the own tenant.
     * A quotation of another tenant looks exactly like a missing one (404) and TenantResolver logs it at WARN.
     */
    private Quotation findForWrite(Long id) {
        Quotation q = quotationRepository.findOne(id, null, tenantResolver.ownerFilter())
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found"));
        tenantResolver.assertWritable(q.getChannelPartnerId(), "Quotation", id);
        return q;
    }

    /** The only way items are read: through a quotation that has already passed the tenant check. */
    private List<QuotationItem> itemsOf(Quotation q) {
        return itemRepository.findForQuotation(q.getId(), q.getChannelPartnerId());
    }

    /** The quotation's customer, only if it belongs to the quotation's own partner. */
    private java.util.Optional<Customer> customerOf(Quotation q) {
        return customerRepository.findById(q.getCustomerId())
                .filter(c -> q.getChannelPartnerId() != null && q.getChannelPartnerId().equals(c.getChannelPartnerId()));
    }

    private Customer findCustomerForCaller(Long customerId) {
        return customerRepository.findOne(customerId, tenantResolver.effectivePartnerId(null),
                        tenantResolver.ownerFilter())
                .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "Customer not found"));
    }

    private void warnIfClientPartner(Long requested) {
        if (requested != null && !requested.equals(tenantResolver.ownPartnerId())) {
            log.warn("[QUOTATION] client partnerId ignored (a Super Admin views a partner with viewPartnerId on a GET) "
                    + "requestedPartnerId={} {}", requested, Actors.describe());
        }
    }

    /** apply(..) with the calculation inputs logged at WARN when it fails. */
    private List<QuotationItem> applyLogged(Quotation q, QuotationRequestDto req, Customer customer, boolean preview) {
        try {
            return apply(q, req, customer, preview);
        } catch (BusinessException ex) {
            log.warn("[QUOTATION] calculation rejected quotationId={} partnerId={} preview={} rule={} inputs={}",
                    q.getId(), q.getChannelPartnerId(), preview, safeReason(ex), calcInputs(req));
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("[QUOTATION] calculation failed quotationId={} partnerId={} preview={} error={} inputs={}",
                    q.getId(), q.getChannelPartnerId(), preview, ex.getClass().getSimpleName(), calcInputs(req));
            throw ex;
        }
    }

    /** error code + message, cut at the first ':' (the only message that carries client text is "Product not found: <name>"). */
    private static String safeReason(BusinessException ex) {
        String m = ex.getMessage() == null ? "" : ex.getMessage();
        int colon = m.indexOf(':');
        return ex.getErrorCode() + " / " + (colon > 0 ? m.substring(0, colon) : m);
    }

    /** Numbers only: no names, mobiles, e-mails, addresses, terms or bank details. */
    private static String calcInputs(QuotationRequestDto r) {
        if (r == null) return "none";
        StringBuilder lines = new StringBuilder();
        int n = 0;
        if (r.getItems() != null) {
            for (QuotationItemRequestDto it : r.getItems()) {
                if (n++ == 20) { lines.append(",..."); break; }
                if (lines.length() > 0) lines.append(',');
                lines.append(it.getQty()).append('x').append(it.getRate());
            }
        }
        return "sizeKw=" + r.getSystemSizeKw()
                + " itemCount=" + (r.getItems() == null ? 0 : r.getItems().size())
                + " lines(qtyxrate)=[" + lines + "]"
                + " installation=" + r.getInstallationCost() + " transport=" + r.getTransportCost()
                + " other=" + r.getOtherCost() + " gstPercent=" + r.getGstPercent()
                + " discount=" + r.getDiscountType() + ":" + r.getDiscountValue()
                + " advance=" + r.getAdvance() + " tariff=" + r.getTariff()
                + " escalation=" + r.getEscalationPercent();
    }

    private String codePrefix(Long partnerId) {
        String p = settingsProvider.quotationDefaults(partnerId).getPrefix();
        return (p == null || p.isBlank()) ? "SQ" : p.trim().toUpperCase(Locale.ROOT);
    }

    private void saveItems(Quotation saved, List<QuotationItem> items) {
        for (QuotationItem it : items) {
            it.setQuotationId(saved.getId());
            it.setChannelPartnerId(saved.getChannelPartnerId());
        }
        itemRepository.saveAll(items);
    }

    /**
     * Copies the request onto the quotation and returns the (unsaved) item lines.
     * Every money value is recalculated here; nothing the client sends for totals is trusted (there is none).
     */
    private List<QuotationItem> apply(Quotation q, QuotationRequestDto req, Customer customer, boolean preview) {
        Long partnerId = q.getChannelPartnerId();
        QuotationDefaultsResponseDto defaults = settingsProvider.quotationDefaults(partnerId);

        // project (optional) must belong to the same customer
        if (req.getProjectId() != null) {
            Project p = projectRepository.findOne(req.getProjectId(), partnerId, tenantResolver.ownerFilter())
                    .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "Project not found"));
            if (!p.getCustomerId().equals(customer.getId())) {
                throw new BusinessException("VALIDATION_ERROR", "Project does not belong to this customer");
            }
        }
        q.setProjectId(req.getProjectId());

        // validity
        LocalDate validUntil = req.getValidUntil() != null
                ? req.getValidUntil()
                : q.getQuoteDate().plusDays(Math.max(defaults.getValidityDays(), 1));
        if (validUntil.isBefore(q.getQuoteDate())) {
            throw new BusinessException("VALIDATION_ERROR", "Valid-until date cannot be before the quotation date");
        }
        q.setValidUntil(validUntil);

        q.setSystemSizeKw(req.getSystemSizeKw().setScale(2, RoundingMode.HALF_UP));
        q.setMounting(blankToNull(req.getMounting()));

        // items
        List<QuotationItem> items = new ArrayList<>();
        BigDecimal itemsTotal = BigDecimal.ZERO;
        int order = 0;
        for (QuotationItemRequestDto r : req.getItems()) {
            if (r.getProductId() != null && productRepository.findOne(r.getProductId(), partnerId).isEmpty()) {
                throw new BusinessException("VALIDATION_ERROR", "Product not found: " + r.getName());
            }
            QuotationItem it = new QuotationItem();
            it.setChannelPartnerId(partnerId);
            it.setSortOrder(order++);
            it.setProductId(r.getProductId());
            it.setName(r.getName().trim());
            it.setType(blankToNull(r.getType()));
            it.setQty(r.getQty().setScale(3, RoundingMode.HALF_UP));
            it.setUnit(r.getUnit().trim());
            it.setRate(r.getRate().setScale(2, RoundingMode.HALF_UP));
            it.setAmount(it.getQty().multiply(it.getRate()).setScale(2, RoundingMode.HALF_UP));
            itemsTotal = itemsTotal.add(it.getAmount());
            items.add(it);
        }

        // extra costs, discount, GST
        q.setInstallationCost(MoneyUtil.scale(req.getInstallationCost()));
        q.setTransportCost(MoneyUtil.scale(req.getTransportCost()));
        q.setOtherLabel(blankToNull(req.getOtherLabel()));
        q.setOtherCost(MoneyUtil.scale(req.getOtherCost()));
        q.setGstPercent(req.getGstPercent().setScale(2, RoundingMode.HALF_UP));
        DiscountType discountType = DiscountType.valueOf(req.getDiscountType());
        BigDecimal discountValue = MoneyUtil.scale(req.getDiscountValue());
        if (discountType == DiscountType.PERCENT && discountValue.compareTo(HUNDRED) > 0) {
            throw new BusinessException("VALIDATION_ERROR", "Discount cannot be more than 100%");
        }
        q.setDiscountType(discountType);
        q.setDiscountValue(discountValue);

        QuotationCalculator.Totals t = QuotationCalculator.totals(itemsTotal, q.getInstallationCost(),
                q.getTransportCost(), q.getOtherCost(), discountType, discountValue, q.getGstPercent());
        if (discountType == DiscountType.FLAT && discountValue.compareTo(t.subtotal) > 0) {
            throw new BusinessException("VALIDATION_ERROR", "Discount cannot be more than the sub total");
        }
        q.setItemsTotal(MoneyUtil.scale(itemsTotal));
        q.setSubtotal(t.subtotal);
        q.setDiscountAmount(t.discountAmount);
        q.setTaxableAmount(t.taxable);
        q.setSgstAmount(t.sgst);
        q.setCgstAmount(t.cgst);
        q.setGstAmount(MoneyUtil.scale(t.sgst.add(t.cgst)));
        q.setTotalAmount(t.total);

        BigDecimal advance = MoneyUtil.scale(req.getAdvance());
        if (advance.compareTo(t.total) > 0) {
            throw new BusinessException("VALIDATION_ERROR", "Advance cannot be more than the total amount");
        }
        q.setAdvance(advance);
        q.setBalanceAmount(t.total.subtract(advance));

        // subsidy + net investment
        String category = blankToNull(customer.getConsumerCategory());
        if (category == null && customer.getType() != null) {
            category = customer.getType().name();
        }
        BigDecimal subsidy = SubsidyCalculator.calculate(category, q.getSystemSizeKw(), t.total,
                settingsProvider.activeSubsidyRules(q.getChannelPartnerId()));
        q.setSubsidyAmount(subsidy);
        q.setNetInvestment(MoneyUtil.max(BigDecimal.ZERO, t.total.subtract(subsidy)).setScale(2, RoundingMode.HALF_UP));

        // savings inputs
        q.setTariff(MoneyUtil.scale(req.getTariff()));
        q.setEscalationPercent(MoneyUtil.scale(req.getEscalationPercent()));

        applyTerms(q, req, defaults);
        applyBank(q, req);
        applyReferral(q, req, defaults, t.total, preview);
        return items;
    }

    private void applyTerms(Quotation q, QuotationRequestDto req, QuotationDefaultsResponseDto defaults) {
        q.setTerms(blankToNull(req.getTerms()) != null ? req.getTerms().trim() : defaults.getTerms());

        BigDecimal adv;
        BigDecimal mat;
        BigDecimal ins;
        BigDecimal bal;
        QuotationRequestDto.PaymentTermsRequest p = req.getPaymentTerms();
        if (p != null) {
            adv = p.getAdvance();
            mat = p.getMaterial();
            ins = p.getInstallation();
            bal = p.getBalance();
        } else {
            QuotationDefaultsResponseDto.PaymentTerms d = defaults.getPaymentTerms();
            adv = d.getAdvance();
            mat = d.getMaterial();
            ins = d.getInstallation();
            bal = d.getBalance();
        }
        BigDecimal sum = adv.add(mat).add(ins).add(bal);
        if (sum.compareTo(HUNDRED) != 0) {
            throw new BusinessException("VALIDATION_ERROR",
                    "Payment terms must add up to 100% (currently " + sum.stripTrailingZeros().toPlainString() + "%)");
        }
        q.setPayAdvancePct(adv.setScale(2, RoundingMode.HALF_UP));
        q.setPayMaterialPct(mat.setScale(2, RoundingMode.HALF_UP));
        q.setPayInstallationPct(ins.setScale(2, RoundingMode.HALF_UP));
        q.setPayBalancePct(bal.setScale(2, RoundingMode.HALF_UP));
    }

    private void applyBank(Quotation q, QuotationRequestDto req) {
        BankDetailsResponseDto d = settingsProvider.bank(q.getChannelPartnerId());
        QuotationRequestDto.BankRequest b = req.getBank();
        q.setBankAccountName(pick(b == null ? null : b.getAccountName(), d.getAccountName()));
        q.setBankName(pick(b == null ? null : b.getBankName(), d.getBankName()));
        q.setBankAccountNumber(pick(b == null ? null : b.getAccountNumber(), d.getAccountNumber()));
        q.setBankIfsc(pick(b == null ? null : b.getIfsc(), d.getIfsc()));
        q.setBankBranch(pick(b == null ? null : b.getBranch(), d.getBranch()));
    }

    private void applyReferral(Quotation q, QuotationRequestDto req, QuotationDefaultsResponseDto defaults,
                               BigDecimal total, boolean preview) {
        QuotationRequestDto.ReferralRequest r = req.getReferral();
        if (r == null || !r.isReferred()) {
            q.setReferred(false);
            q.setReferredBy(null);
            q.setReferrerType(null);
            q.setReferralCode(null);
            q.setReferrerPartnerId(null);
            q.setReferralPercent(null);
            q.setReferralAmount(BigDecimal.ZERO.setScale(2));
            q.setReferralStatus(null);
            return;
        }
        String by = blankToNull(r.getReferredBy());
        if (by == null) {
            throw new BusinessException("VALIDATION_ERROR", "Referred by is required when the quotation is a referral");
        }
        BigDecimal percent = r.getReferralPercent() != null ? r.getReferralPercent()
                : (defaults.getCustomerReferralPercent() != null ? defaults.getCustomerReferralPercent() : BigDecimal.ZERO);
        String code = blankToNull(r.getReferralCode());
        if (code == null) {
            code = q.getReferralCode() != null ? q.getReferralCode() : (preview ? null : generateReferralCode(by));
        }
        boolean wasReferred = q.isReferred();
        q.setReferred(true);
        q.setReferredBy(by);
        q.setReferrerType(blankToNull(r.getReferrerType()));
        q.setReferralCode(code);
        q.setReferrerPartnerId(r.getReferrerPartnerId());
        q.setReferralPercent(percent.setScale(2, RoundingMode.HALF_UP));
        q.setReferralAmount(MoneyUtil.percentOf(total, percent));
        if (!wasReferred || q.getReferralStatus() == null) {
            // starts as PENDING only once the quotation is accepted; until then it stays empty
            q.setReferralStatus(q.getStatus() == QuotationStatus.ACCEPTED ? ReferralStatus.PENDING : null);
        }
    }

    /** Initials of the name + 4 digits, for example "Priya Nair" -> PN4821. */
    private String generateReferralCode(String name) {
        StringBuilder initials = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0)) && initials.length() < 3) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        if (initials.length() == 0) {
            initials.append("REF");
        }
        return initials + String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000));
    }

    private QuotationStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return QuotationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("VALIDATION_ERROR", "Unknown quotation status " + value);
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String pick(String value, String fallback) {
        String v = blankToNull(value);
        return v != null ? v : fallback;
    }

    private Map<String, Object> snapshot(Quotation q, List<QuotationItem> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", q.getCustomerId() == null ? null : q.getCustomerId().toString());
        m.put("projectId", q.getProjectId() == null ? null : q.getProjectId().toString());
        m.put("status", q.getStatus().name());
        m.put("validUntil", q.getValidUntil() == null ? null : q.getValidUntil().toString());
        m.put("systemSizeKw", plain(q.getSystemSizeKw()));
        m.put("mounting", q.getMounting());
        StringBuilder sb = new StringBuilder();
        for (QuotationItem it : items) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(it.getName()).append(" x").append(plain(it.getQty())).append(" @").append(plain(it.getRate()));
        }
        m.put("items", sb.toString());
        m.put("installationCost", plain(q.getInstallationCost()));
        m.put("transportCost", plain(q.getTransportCost()));
        m.put("otherCost", plain(q.getOtherCost()));
        m.put("discountType", q.getDiscountType().name());
        m.put("discountValue", plain(q.getDiscountValue()));
        m.put("gstPercent", plain(q.getGstPercent()));
        m.put("totalAmount", plain(q.getTotalAmount()));
        m.put("advance", plain(q.getAdvance()));
        m.put("subsidyAmount", plain(q.getSubsidyAmount()));
        m.put("tariff", plain(q.getTariff()));
        m.put("escalationPercent", plain(q.getEscalationPercent()));
        m.put("referredBy", q.isReferred() ? q.getReferredBy() : null);
        m.put("referralPercent", plain(q.getReferralPercent()));
        m.put("referralAmount", plain(q.getReferralAmount()));
        m.put("referralStatus", q.getReferralStatus() == null ? null : q.getReferralStatus().name());
        m.put("payAdvancePct", plain(q.getPayAdvancePct()));
        m.put("payMaterialPct", plain(q.getPayMaterialPct()));
        m.put("payInstallationPct", plain(q.getPayInstallationPct()));
        m.put("payBalancePct", plain(q.getPayBalancePct()));
        m.put("terms", q.getTerms());
        return m;
    }

    private String plain(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    // ---------------------------------------------------------------- mapping

    private static final class ListLookup {
        private Map<Long, PartnerRefResponseDto> partners = new HashMap<>();
        private Map<Long, String> names = new HashMap<>();
        private final Map<Long, CustomerRefResponseDto> customers = new HashMap<>();
    }

    private ListLookup listLookup(List<Quotation> rows, Long partnerId) {
        ListLookup lk = new ListLookup();
        if (rows.isEmpty()) {
            return lk;
        }
        Set<Long> customerIds = new HashSet<>();
        Set<Long> partnerIds = new HashSet<>();
        for (Quotation q : rows) {
            customerIds.add(q.getCustomerId());
            partnerIds.add(q.getChannelPartnerId());
        }
        lk.partners = partnerRefProvider.refs(partnerIds);
        lk.names = auditInfoMapper.namesFor(rows);
        for (Customer c : customerRepository.findAllById(customerIds)) {
            if (partnerId.equals(c.getChannelPartnerId())) {           // never name a customer of another tenant
                lk.customers.put(c.getId(), new CustomerRefResponseDto(c.getId(), c.getCode(), c.getName()));
            }
        }
        return lk;
    }

    private QuotationListItemResponseDto toListItem(Quotation q, ListLookup lk) {
        QuotationListItemResponseDto d = new QuotationListItemResponseDto();
        d.setId(q.getId());
        d.setCode(q.getCode());
        d.setCustomer(lk.customers.get(q.getCustomerId()));
        d.setSystemSizeKw(q.getSystemSizeKw());
        d.setTotalAmount(q.getTotalAmount());
        d.setBalanceAmount(q.getBalanceAmount());
        d.setStatus(q.getStatus().name());
        d.setQuoteDate(q.getQuoteDate());
        d.setValidUntil(q.getValidUntil());
        d.setReferred(q.isReferred());
        d.setReferredBy(q.getReferredBy());
        d.setPartner(lk.partners.get(q.getChannelPartnerId()));
        d.setAudit(auditInfoMapper.of(q, lk.names));
        return d;
    }

    private QuotationResponseDto toResponse(Quotation q, List<QuotationItem> items) {
        QuotationResponseDto d = new QuotationResponseDto();
        d.setId(q.getId());
        d.setCode(q.getCode());
        d.setStatus(q.getStatus().name());
        d.setQuoteDate(q.getQuoteDate());
        d.setValidUntil(q.getValidUntil());
        d.setProjectId(q.getProjectId());
        d.setSystemSizeKw(q.getSystemSizeKw());
        d.setMounting(q.getMounting());

        Customer c = customerOf(q).orElse(null);
        if (c != null) {
            QuotationResponseDto.CustomerBlock cb = new QuotationResponseDto.CustomerBlock();
            cb.setId(c.getId());
            cb.setCode(c.getCode());
            cb.setName(c.getName());
            cb.setMobile(c.getMobile());
            cb.setEmail(c.getEmail());
            cb.setAddress(c.getAddress());
            cb.setCity(c.getCity());
            cb.setState(c.getState());
            cb.setPincode(c.getPincode());
            cb.setGstNo(c.getGstNo());
            cb.setType(c.getType() == null ? null : c.getType().name());
            cb.setConsumerNo(c.getConsumerNo());
            cb.setConsumerCategory(c.getConsumerCategory());
            d.setCustomer(cb);
        }

        List<QuotationItemResponseDto> itemDtos = new ArrayList<>();
        for (QuotationItem it : items) {
            QuotationItemResponseDto i = new QuotationItemResponseDto();
            i.setId(it.getId());
            i.setProductId(it.getProductId());
            i.setName(it.getName());
            i.setType(it.getType());
            i.setQty(it.getQty());
            i.setUnit(it.getUnit());
            i.setRate(it.getRate());
            i.setAmount(it.getAmount());
            itemDtos.add(i);
        }
        d.setItems(itemDtos);

        d.setItemsTotal(q.getItemsTotal());
        d.setInstallationCost(q.getInstallationCost());
        d.setTransportCost(q.getTransportCost());
        d.setOtherLabel(q.getOtherLabel());
        d.setOtherCost(q.getOtherCost());
        d.setSubtotal(q.getSubtotal());
        d.setDiscountType(q.getDiscountType().name());
        d.setDiscountValue(q.getDiscountValue());
        d.setDiscountAmount(q.getDiscountAmount());
        d.setGstPercent(q.getGstPercent());
        d.setTaxableAmount(q.getTaxableAmount());
        d.setSgstAmount(q.getSgstAmount());
        d.setCgstAmount(q.getCgstAmount());
        d.setTotalAmount(q.getTotalAmount());
        d.setAmountInWords(NumberToWords.rupees(q.getTotalAmount().setScale(0, RoundingMode.HALF_UP).longValue()));
        d.setAdvance(q.getAdvance());
        d.setBalanceAmount(q.getBalanceAmount());
        d.setSubsidyAmount(q.getSubsidyAmount());
        d.setNetInvestment(q.getNetInvestment());

        d.setTariff(q.getTariff());
        d.setEscalationPercent(q.getEscalationPercent());
        d.setSavings(QuotationCalculator.savings(q.getSystemSizeKw(), q.getTariff(), q.getEscalationPercent(),
                q.getNetInvestment(), settingsProvider.savingsConfig(q.getChannelPartnerId())));

        d.setTerms(q.getTerms());
        QuotationResponseDto.PaymentTerms pt = new QuotationResponseDto.PaymentTerms();
        pt.setAdvance(q.getPayAdvancePct());
        pt.setMaterial(q.getPayMaterialPct());
        pt.setInstallation(q.getPayInstallationPct());
        pt.setBalance(q.getPayBalancePct());
        d.setPaymentTerms(pt);

        QuotationResponseDto.Bank b = new QuotationResponseDto.Bank();
        b.setAccountName(q.getBankAccountName());
        b.setBankName(q.getBankName());
        b.setAccountNumber(q.getBankAccountNumber());
        b.setIfsc(q.getBankIfsc());
        b.setBranch(q.getBankBranch());
        d.setBank(b);

        QuotationResponseDto.Referral r = new QuotationResponseDto.Referral();
        r.setReferred(q.isReferred());
        r.setReferredBy(q.getReferredBy());
        r.setReferrerType(q.getReferrerType());
        r.setReferralCode(q.getReferralCode());
        r.setReferrerPartnerId(q.getReferrerPartnerId());
        r.setReferralPercent(q.getReferralPercent());
        r.setReferralAmount(q.getReferralAmount());
        r.setReferralStatus(q.getReferralStatus() == null ? null : q.getReferralStatus().name());
        d.setReferral(r);

        d.setPartner(partnerRefProvider.ref(q.getChannelPartnerId()));
        d.setAudit(auditInfoMapper.of(q, auditInfoMapper.namesFor(List.of(q))));
        return d;
    }
}