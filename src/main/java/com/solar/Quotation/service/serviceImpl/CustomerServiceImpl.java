package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.CustomerType;
import com.solar.Quotation.Enum.FollowupStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.CustomerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerStatusRequestDto;
import com.solar.Quotation.dto.requestdto.FollowupFilterRequestDto;
import com.solar.Quotation.dto.requestdto.FollowupRequestDto;
import com.solar.Quotation.dto.responsedto.AuditLogResponseDto;
import com.solar.Quotation.dto.responsedto.CustomerResponseDto;
import com.solar.Quotation.dto.responsedto.CustomerSummaryResponseDto;
import com.solar.Quotation.dto.responsedto.FollowupResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.CustomerFollowup;
import com.solar.Quotation.entity.Project;
import com.solar.Quotation.exception.ApiException;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.CustomerFollowupRepository;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.ProjectRepository;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.CustomerService;
import com.solar.Quotation.service.PartnerRefProvider;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Tenant rules (same as Products; Super Admin works in the PLATFORM tenant like any other partner):
 *   READS   list / summary / get / followups / history -> tenantResolver.effectiveReadPartnerId(): own tenant, or the
 *           partner named by ?viewPartnerId= for data scope ALL on a GET. A partnerId in a filter is ignored.
 *   WRITES  create / update / status / delete / add + update follow-up -> always the caller's own tenant. The record is
 *           loaded by id and refused with 404 (+ WARN) when it belongs to another tenant (assertWritable).
 *   OWNER   data scope OWN (tenantResolver.ownerFilter()) is applied to customers on every path, and to follow-ups
 *           through their customer, so an OWN user never reaches somebody else's customer or its follow-ups.
 *   FOLLOW-UPS are never loaded by their own id alone: the customer is tenant-checked first, and the follow-up is then
 *           looked up by (id, customerId, partnerId).
 *
 * Audit: every create / update / status change / delete (and follow-up add / change) writes an audit row with
 * previous and new values (DiffUtil.diff / DiffUtil.removed), under the record's own partnerId.
 * Logging: [CUSTOMER] INFO for writes, WARN for duplicates, validation and cross-tenant attempts, ERROR with stack
 * for anything unexpected in a write. Names, mobiles and e-mails are never logged, only ids and codes.
 */
@Service
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "name", "code", "city", "status");
    private static final Set<String> FOLLOWUP_SORT_FIELDS = Set.of("followupDate", "nextDate", "createdAt");
    /** "Today" for overdue follow-ups is the business day in India, not the server's zone. */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final CustomerRepository customerRepository;
    private final CustomerFollowupRepository followupRepository;
    private final ProjectRepository projectRepository;
    private final TenantResolver tenantResolver;
    private final PartnerRefProvider partnerRefProvider;
    private final AuditInfoMapper auditInfoMapper;
    private final AuditService auditService;
    private final CodeGenerator codeGenerator;
    private final PartnerGuard partnerGuard;

    public CustomerServiceImpl(CustomerRepository customerRepository, CustomerFollowupRepository followupRepository,
                               ProjectRepository projectRepository, TenantResolver tenantResolver,
                               PartnerRefProvider partnerRefProvider, AuditInfoMapper auditInfoMapper,
                               AuditService auditService, CodeGenerator codeGenerator, PartnerGuard partnerGuard) {
        this.customerRepository = customerRepository;
        this.followupRepository = followupRepository;
        this.projectRepository = projectRepository;
        this.tenantResolver = tenantResolver;
        this.partnerRefProvider = partnerRefProvider;
        this.auditInfoMapper = auditInfoMapper;
        this.auditService = auditService;
        this.codeGenerator = codeGenerator;
        this.partnerGuard = partnerGuard;
    }

    // ---------------------------------------------------------------- customers: read

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponseDto> list(CustomerFilterRequestDto f) {
        warnIfClientPartner(f.getPartnerId());
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        Long ownerId = tenantResolver.ownerFilter();
        Page<Customer> page = customerRepository.search(partnerId, ownerId, parseType(f.getType()),
                parseStatus(f.getStatus()), f.getSearchOrNull(), f.toPageable("createdAt,desc", SORT_FIELDS));
        Lookup lookup = lookup(page.getContent());
        log.debug("[CUSTOMER] list partnerId={} rows={} total={}", partnerId, page.getNumberOfElements(), page.getTotalElements());
        return PageResponse.of(page.map(c -> toResponse(c, lookup)));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerSummaryResponseDto summary() {
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        Long ownerId = tenantResolver.ownerFilter();
        CustomerSummaryResponseDto s = new CustomerSummaryResponseDto();
        s.setTotal(customerRepository.countFiltered(partnerId, ownerId, null, null));
        s.setActive(customerRepository.countFiltered(partnerId, ownerId, null, RecordStatus.ACTIVE));
        s.setInactive(customerRepository.countFiltered(partnerId, ownerId, null, RecordStatus.INACTIVE));
        s.setResidential(customerRepository.countFiltered(partnerId, ownerId, CustomerType.RESIDENTIAL, null));
        s.setCommercial(customerRepository.countFiltered(partnerId, ownerId, CustomerType.COMMERCIAL, null));
        s.setIndustrial(customerRepository.countFiltered(partnerId, ownerId, CustomerType.INDUSTRIAL, null));
        return s;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponseDto get(Long id) {
        return toSingleResponse(findForRead(id));
    }

    // ---------------------------------------------------------------- customers: write

    @Override
    @Transactional
    public CustomerResponseDto create(CustomerRequestDto req) {
        return guarded("create", () -> doCreate(req));
    }

    private CustomerResponseDto doCreate(CustomerRequestDto req) {
        // always the caller's own tenant; a partnerId in the body is passed on only so TenantResolver can log it at WARN
        Long partnerId = tenantResolver.requirePartnerIdForCreate(req.getPartnerId());
        partnerGuard.assertUsable(partnerId);

        String mobile = req.getMobile().trim();
        if (customerRepository.existsByChannelPartnerIdAndMobileAndDeletedFalse(partnerId, mobile)) {
            throw duplicateMobile(partnerId);
        }

        Customer c = new Customer();
        c.setChannelPartnerId(partnerId);
        c.setCode(codeGenerator.next("CUST", partnerId));
        c.setMobile(mobile);
        applyFields(c, req);
        Customer saved = customerRepository.save(c);

        auditService.record(AuditAction.CREATE, "CUSTOMER", "Customer", saved.getId(), saved.getCode(),
                partnerId, null, snapshot(saved));
        log.info("[CUSTOMER] created id={} code={} partnerId={} type={}", saved.getId(), saved.getCode(), partnerId, saved.getType());
        return toSingleResponse(saved);
    }

    @Override
    @Transactional
    public CustomerResponseDto update(Long id, CustomerRequestDto req) {
        return guarded("update", () -> doUpdate(id, req));
    }

    private CustomerResponseDto doUpdate(Long id, CustomerRequestDto req) {
        Customer c = findForWrite(id);
        String mobile = req.getMobile().trim();
        if (!mobile.equals(c.getMobile())
                && customerRepository.existsByChannelPartnerIdAndMobileAndDeletedFalseAndIdNot(
                c.getChannelPartnerId(), mobile, c.getId())) {
            throw duplicateMobile(c.getChannelPartnerId());
        }

        Map<String, Object> before = snapshot(c);
        c.setMobile(mobile);
        applyFields(c, req);
        Customer saved = customerRepository.save(c);

        auditService.record(AuditAction.UPDATE, "CUSTOMER", "Customer", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), before, snapshot(saved));
        log.info("[CUSTOMER] updated id={} code={} partnerId={}", saved.getId(), saved.getCode(), saved.getChannelPartnerId());
        return toSingleResponse(saved);
    }

    @Override
    @Transactional
    public CustomerResponseDto updateStatus(Long id, CustomerStatusRequestDto req) {
        return guarded("status change", () -> doUpdateStatus(id, req));
    }

    private CustomerResponseDto doUpdateStatus(Long id, CustomerStatusRequestDto req) {
        Customer c = findForWrite(id);
        Map<String, Object> before = snapshot(c);
        c.setStatus(RecordStatus.valueOf(req.getStatus()));
        Customer saved = customerRepository.save(c);

        auditService.record(AuditAction.STATUS_CHANGE, "CUSTOMER", "Customer", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), before, snapshot(saved));
        log.info("[CUSTOMER] status changed id={} code={} partnerId={} {}->{}", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), before.get("status"), saved.getStatus());
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
        Customer c = findForWrite(id);
        // Add the same check for quotations when the quotation module exists.
        if (projectRepository.countByCustomerIdAndDeletedFalse(c.getId()) > 0) {
            throw invalid("BUSINESS_RULE", "This customer has projects. Delete or move them first",
                    "delete refused, customer has projects id=" + id);
        }
        Map<String, Object> before = snapshot(c);          // taken BEFORE the mobile is suffixed
        String code = c.getCode();
        Long partnerId = c.getChannelPartnerId();
        c.setMobile(c.getMobile() + "~del" + c.getId());      // frees the mobile number for reuse
        c.setStatus(RecordStatus.INACTIVE);
        c.setDeleted(true);
        customerRepository.save(c);

        auditService.record(AuditAction.DELETE, "CUSTOMER", "Customer", id, code, partnerId, before, null);
        log.info("[CUSTOMER] deleted id={} code={} partnerId={}", id, code, partnerId);
    }

    // ---------------------------------------------------------------- follow-ups (always through a tenant-checked customer)

    @Override
    @Transactional(readOnly = true)
    public List<FollowupResponseDto> followups(Long customerId) {
        Customer c = findForRead(customerId);
        PartnerRefResponseDto partner = partnerRefProvider.refs(Set.of(c.getChannelPartnerId())).get(c.getChannelPartnerId());
        LocalDate today = today();
        return followupRepository
                .findByCustomerIdAndChannelPartnerIdAndDeletedFalseOrderByFollowupDateDescIdDesc(c.getId(), c.getChannelPartnerId())
                .stream()
                .map(f -> toFollowupResponse(f, c, partner, today))
                .toList();
    }

    @Override
    @Transactional
    public FollowupResponseDto addFollowup(Long customerId, FollowupRequestDto req) {
        return guarded("follow-up add", () -> doAddFollowup(customerId, req));
    }

    private FollowupResponseDto doAddFollowup(Long customerId, FollowupRequestDto req) {
        Customer c = findForWrite(customerId);
        validateFollowupDates(req);

        CustomerFollowup f = new CustomerFollowup();
        f.setChannelPartnerId(c.getChannelPartnerId());          // inherited from the customer, never from the client
        f.setCustomerId(c.getId());
        applyFollowup(f, req);
        CustomerFollowup saved = followupRepository.save(f);

        // shows up in the customer's history
        auditService.record(AuditAction.UPDATE, "CUSTOMER", "Customer", c.getId(), c.getCode(),
                c.getChannelPartnerId(), null, followupSnapshot(saved));
        log.info("[CUSTOMER] follow-up added id={} customerId={} partnerId={} status={}", saved.getId(), c.getId(),
                c.getChannelPartnerId(), saved.getStatus());
        return toFollowupSingle(saved, c);
    }

    @Override
    @Transactional
    public FollowupResponseDto updateFollowup(Long customerId, Long followupId, FollowupRequestDto req) {
        return guarded("follow-up update", () -> doUpdateFollowup(customerId, followupId, req));
    }

    private FollowupResponseDto doUpdateFollowup(Long customerId, Long followupId, FollowupRequestDto req) {
        Customer c = findForWrite(customerId);
        validateFollowupDates(req);
        CustomerFollowup f = followupRepository
                .findByIdAndCustomerIdAndChannelPartnerIdAndDeletedFalse(followupId, c.getId(), c.getChannelPartnerId())
                .orElseThrow(() -> {
                    log.warn("[CUSTOMER] follow-up not found for customer id={} followupId={} partnerId={}",
                            c.getId(), followupId, c.getChannelPartnerId());
                    return new ResourceNotFoundException("Follow-up not found");
                });

        Map<String, Object> before = followupSnapshot(f);
        applyFollowup(f, req);
        CustomerFollowup saved = followupRepository.save(f);

        auditService.record(AuditAction.UPDATE, "CUSTOMER", "Customer", c.getId(), c.getCode(),
                c.getChannelPartnerId(), before, followupSnapshot(saved));
        log.info("[CUSTOMER] follow-up updated id={} customerId={} partnerId={} status={}->{}", saved.getId(), c.getId(),
                c.getChannelPartnerId(), before.get("followupStatus"), saved.getStatus());
        return toFollowupSingle(saved, c);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FollowupResponseDto> allFollowups(FollowupFilterRequestDto f) {
        warnIfClientPartner(f.getPartnerId());
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        Long ownerId = tenantResolver.ownerFilter();
        if (f.getCustomerId() != null) {
            findForRead(f.getCustomerId());       // 404 when that customer is not visible to the caller
        }

        // PENDING, COMPLETED and OVERDUE are expressed as (stored status, date rule)
        FollowupStatus stored = null;
        int mode = 0;
        String wanted = f.getStatus() == null ? "" : f.getStatus().trim().toUpperCase(Locale.ROOT);
        switch (wanted) {
            case "" -> { }
            case "COMPLETED" -> stored = FollowupStatus.COMPLETED;
            case "OVERDUE" -> { stored = FollowupStatus.PENDING; mode = 1; }
            case "PENDING" -> { stored = FollowupStatus.PENDING; mode = 2; }
            default -> throw invalid("VALIDATION_ERROR", "Unknown follow-up status " + f.getStatus(),
                    "unknown follow-up status " + clean(f.getStatus()));
        }

        LocalDate today = today();
        Page<CustomerFollowup> page = followupRepository.search(partnerId, ownerId, f.getCustomerId(), stored, mode,
                today, f.getSearchOrNull(), f.toPageable("followupDate,desc", FOLLOWUP_SORT_FIELDS));

        Set<Long> customerIds = new HashSet<>();
        for (CustomerFollowup x : page.getContent()) {
            customerIds.add(x.getCustomerId());
        }
        Map<Long, Customer> customers = new HashMap<>();
        if (!customerIds.isEmpty()) {
            for (Customer c : customerRepository.findAllById(customerIds)) {
                if (partnerId.equals(c.getChannelPartnerId())) {       // never name a customer of another tenant
                    customers.put(c.getId(), c);
                }
            }
        }
        Map<Long, PartnerRefResponseDto> partners = partnerRefProvider.refs(Set.of(partnerId));
        final LocalDate todayFinal = today;
        return PageResponse.of(page.map(x -> toFollowupResponse(
                x, customers.get(x.getCustomerId()), partners.get(x.getChannelPartnerId()), todayFinal)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> history(Long customerId) {
        Customer c = findForRead(customerId);          // 404 if it is not visible to the caller
        return auditService.entityHistory("Customer", c.getId());
    }

    // ---------------------------------------------------------------- tenant helpers

    /** GET: own tenant, or the viewed partner for Super Admin; data scope OWN applies. */
    private Customer findForRead(Long id) {
        return customerRepository.findOne(id, tenantResolver.effectiveReadPartnerId(), tenantResolver.ownerFilter())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    /**
     * Writes: loads by id (data scope OWN still applies), then refuses anything outside the own tenant.
     * A customer of another tenant looks exactly like a missing one (404) and TenantResolver logs it at WARN.
     */
    private Customer findForWrite(Long id) {
        Customer c = customerRepository.findOne(id, null, tenantResolver.ownerFilter())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        tenantResolver.assertWritable(c.getChannelPartnerId(), "Customer", id);
        return c;
    }

    private void warnIfClientPartner(Long requested) {
        if (requested != null) {
            log.warn("[CUSTOMER] client partnerId ignored on read requestedPartnerId={}", requested);
        }
    }

    private BusinessException duplicateMobile(Long partnerId) {
        log.warn("[CUSTOMER] duplicate mobile partnerId={}", partnerId);
        return new BusinessException("DUPLICATE", "A customer with this mobile number already exists", HttpStatus.CONFLICT);
    }

    /** Logs a validation problem at WARN and returns the exception to throw. */
    private BusinessException invalid(String code, String message, String logDetail) {
        log.warn("[CUSTOMER] validation: {}", logDetail);
        return new BusinessException(code, message);
    }

    /** Runs a write; anything unexpected is logged at ERROR with the stack, then rethrown to the API error handler. */
    private <T> T guarded(String operation, Supplier<T> body) {
        try {
            return body.get();
        } catch (BusinessException | ResourceNotFoundException | ForbiddenException | ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[CUSTOMER] {} failed unexpectedly", operation, e);
            throw e;
        }
    }

    /** Strips control characters from user text before it goes into a log line (log forging). */
    private static String clean(String s) {
        if (s == null) return "-";
        String t = s.replaceAll("[\\r\\n\\t]", " ");
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    private void applyFields(Customer c, CustomerRequestDto req) {
        c.setName(req.getName().trim());
        c.setEmail(blankToNull(req.getEmail()));
        c.setAddress(blankToNull(req.getAddress()));
        c.setCity(blankToNull(req.getCity()));
        c.setState(blankToNull(req.getState()));
        c.setPincode(blankToNull(req.getPincode()));
        String gst = blankToNull(req.getGstNo());
        c.setGstNo(gst == null ? null : gst.toUpperCase(Locale.ROOT));
        c.setType(CustomerType.valueOf(req.getType()));
        c.setConsumerNo(blankToNull(req.getConsumerNo()));
        c.setConsumerCategory(blankToNull(req.getConsumerCategory()));
        RecordStatus status = parseStatus(req.getStatus());
        c.setStatus(status != null ? status : (c.getStatus() != null ? c.getStatus() : RecordStatus.ACTIVE));
    }

    private void validateFollowupDates(FollowupRequestDto req) {
        if (req.getNextDate() != null && req.getNextDate().isBefore(req.getFollowupDate())) {
            throw invalid("VALIDATION_ERROR", "Next date cannot be before the follow-up date",
                    "follow-up next date is before the follow-up date");
        }
    }

    private void applyFollowup(CustomerFollowup f, FollowupRequestDto req) {
        f.setFollowupDate(req.getFollowupDate());
        f.setRemark(req.getRemark().trim());
        f.setNextDate(req.getNextDate());
        f.setStatus(req.getStatus() == null ? FollowupStatus.PENDING : FollowupStatus.valueOf(req.getStatus()));
    }

    private LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private String displayStatus(CustomerFollowup f, LocalDate today) {
        if (f.getStatus() == FollowupStatus.COMPLETED) {
            return "COMPLETED";
        }
        return (f.getNextDate() != null && f.getNextDate().isBefore(today)) ? "OVERDUE" : "PENDING";
    }

    private CustomerType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CustomerType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("VALIDATION_ERROR", "Unknown customer type " + value, "unknown customer type " + clean(value));
        }
    }

    private RecordStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RecordStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("VALIDATION_ERROR", "Unknown status " + value, "unknown status " + clean(value));
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private Map<String, Object> snapshot(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.getName());
        m.put("mobile", c.getMobile());
        m.put("email", c.getEmail());
        m.put("address", c.getAddress());
        m.put("city", c.getCity());
        m.put("state", c.getState());
        m.put("pincode", c.getPincode());
        m.put("gstNo", c.getGstNo());
        m.put("type", c.getType().name());
        m.put("consumerNo", c.getConsumerNo());
        m.put("consumerCategory", c.getConsumerCategory());
        m.put("status", c.getStatus().name());
        return m;
    }

    private Map<String, Object> followupSnapshot(CustomerFollowup f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("followupDate", f.getFollowupDate() == null ? null : f.getFollowupDate().toString());
        m.put("followupRemark", f.getRemark());
        m.put("followupNextDate", f.getNextDate() == null ? null : f.getNextDate().toString());
        m.put("followupStatus", f.getStatus().name());
        return m;
    }

    /** Batch lookups so a page of customers costs a fixed number of extra queries. */
    private static final class Lookup {
        private Map<Long, PartnerRefResponseDto> partners = new HashMap<>();
        private Map<Long, String> names = new HashMap<>();
        private final Map<Long, Long> projectCounts = new HashMap<>();
        private final Map<Long, String> latestProject = new HashMap<>();
    }

    private Lookup lookup(List<Customer> customers) {
        Lookup lk = new Lookup();
        if (customers.isEmpty()) {
            return lk;
        }
        Set<Long> ids = new HashSet<>();
        Set<Long> partnerIds = new HashSet<>();
        for (Customer c : customers) {
            ids.add(c.getId());
            partnerIds.add(c.getChannelPartnerId());
        }
        lk.partners = partnerRefProvider.refs(partnerIds);
        lk.names = auditInfoMapper.namesFor(customers);
        for (Object[] row : projectRepository.countGroupedByCustomer(ids)) {
            lk.projectCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        for (Project p : projectRepository.findNewestForCustomers(ids)) {
            lk.latestProject.putIfAbsent(p.getCustomerId(), p.getName());
        }
        return lk;
    }

    private CustomerResponseDto toSingleResponse(Customer c) {
        return toResponse(c, lookup(List.of(c)));
    }

    private CustomerResponseDto toResponse(Customer c, Lookup lk) {
        CustomerResponseDto d = new CustomerResponseDto();
        d.setId(c.getId());
        d.setCode(c.getCode());
        d.setName(c.getName());
        d.setMobile(c.getMobile());
        d.setEmail(c.getEmail());
        d.setAddress(c.getAddress());
        d.setCity(c.getCity());
        d.setState(c.getState());
        d.setPincode(c.getPincode());
        d.setGstNo(c.getGstNo());
        d.setType(c.getType().name());
        d.setConsumerNo(c.getConsumerNo());
        d.setConsumerCategory(c.getConsumerCategory());
        d.setStatus(c.getStatus().name());
        d.setProjectSummary(lk.latestProject.get(c.getId()));
        d.setProjectCount(lk.projectCounts.getOrDefault(c.getId(), 0L));
        d.setQuotationCount(0L);                       // filled when the quotation module exists
        d.setPartner(lk.partners.get(c.getChannelPartnerId()));
        d.setAudit(auditInfoMapper.of(c, lk.names));
        return d;
    }

    private FollowupResponseDto toFollowupSingle(CustomerFollowup f, Customer c) {
        PartnerRefResponseDto partner = partnerRefProvider.refs(Set.of(c.getChannelPartnerId())).get(c.getChannelPartnerId());
        return toFollowupResponse(f, c, partner, today());
    }

    private FollowupResponseDto toFollowupResponse(CustomerFollowup f, Customer c, PartnerRefResponseDto partner,
                                                   LocalDate today) {
        FollowupResponseDto d = new FollowupResponseDto();
        d.setId(f.getId());
        d.setCustomerId(f.getCustomerId());
        d.setCustomerCode(c == null ? null : c.getCode());
        d.setCustomerName(c == null ? null : c.getName());
        d.setFollowupDate(f.getFollowupDate());
        d.setRemark(f.getRemark());
        d.setNextDate(f.getNextDate());
        d.setStatus(displayStatus(f, today));
        d.setPartner(partner);
        return d;
    }
}