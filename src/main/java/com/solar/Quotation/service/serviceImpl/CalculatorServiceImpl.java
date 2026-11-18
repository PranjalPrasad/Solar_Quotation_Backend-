package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.ProjectType;
import com.solar.Quotation.dto.requestdto.CalculationFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CalculationPreviewRequestDto;
import com.solar.Quotation.dto.requestdto.CalculationRequestDto;
import com.solar.Quotation.dto.responsedto.CalculationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.CalculationResponseDto;
import com.solar.Quotation.dto.responsedto.GstRuleResponseDto;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.entity.CostCalculation;
import com.solar.Quotation.exception.ApiException;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.CostCalculationRepository;
import com.solar.Quotation.security.Actors;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.CalculatorService;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.SettingsProvider;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.CostCalculator;
import com.solar.Quotation.util.DiffUtil;
import com.solar.Quotation.util.MoneyUtil;
import com.solar.Quotation.util.PartnerGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Tenant rules:
 *   READS   list / get -> tenantResolver.effectiveReadPartnerId() (own tenant, or viewed partner for Super Admin).
 *   WRITES  create / update / delete -> always the caller's own tenant.
 *   OWNER   data scope OWN (tenantResolver.ownerFilter()) is applied on every path.
 * Settings (savings config, subsidy rules, GST rules) are read for the record's own partner.
 * Audit rows are written for create / update / delete.
 */
@Service
public class CalculatorServiceImpl implements CalculatorService {

    private static final Logger log = LoggerFactory.getLogger(CalculatorServiceImpl.class);

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "code", "customerName", "capacityKw", "grandTotal");
    /** Used only when the tenant has no PRODUCT GST rule and the request has no gstPercent. */
    static final BigDecimal FALLBACK_GST_PERCENT = new BigDecimal("18");

    private final CostCalculationRepository calcRepo;
    private final TenantResolver tenantResolver;
    private final PartnerRefProvider partnerRefProvider;
    private final AuditService auditService;
    private final CodeGenerator codeGenerator;
    private final PartnerGuard partnerGuard;
    private final SettingsProvider settingsProvider;

    public CalculatorServiceImpl(CostCalculationRepository calcRepo, TenantResolver tenantResolver,
                                 PartnerRefProvider partnerRefProvider, AuditService auditService,
                                 CodeGenerator codeGenerator, PartnerGuard partnerGuard,
                                 SettingsProvider settingsProvider) {
        this.calcRepo = calcRepo;
        this.tenantResolver = tenantResolver;
        this.partnerRefProvider = partnerRefProvider;
        this.auditService = auditService;
        this.codeGenerator = codeGenerator;
        this.partnerGuard = partnerGuard;
        this.settingsProvider = settingsProvider;
    }

    // ---------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public CalculationDefaultsResponseDto defaults() {
        Long partnerId = tenantResolver.ownPartnerId();
        return new CalculationDefaultsResponseDto(defaultGst(partnerId),
                settingsProvider.savingsConfig(partnerId).getMarketRatePerKw());
    }

    @Override
    @Transactional(readOnly = true)
    public CalculationResponseDto preview(CalculationPreviewRequestDto req) {
        return guarded("preview", () -> {
            Long partnerId = tenantResolver.ownPartnerId();
            Calculated c = calculate(req, partnerId);
            return toPreviewDto(req, c, partnerId);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public CalculationResponseDto getById(Long id) {
        return toSingleDto(findForRead(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CalculationResponseDto> list(CalculationFilterRequestDto f) {
        if (f.getPartnerId() != null) {
            log.warn("[CALC] client partnerId ignored on read requestedPartnerId={}", f.getPartnerId());
        }
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        Long ownerId = tenantResolver.ownerFilter();
        Page<CostCalculation> page = calcRepo.search(partnerId, ownerId, parseType(f.getProjectType()),
                f.getSearchOrNull(), f.toPageable("createdAt,desc", SORT_FIELDS));
        Map<Long, PartnerRefResponseDto> partners = partnerRefProvider.refs(
                new HashSet<>(page.getContent().stream().map(CostCalculation::getChannelPartnerId).toList()));
        log.debug("[CALC] list partnerId={} rows={} total={}", partnerId, page.getNumberOfElements(), page.getTotalElements());
        return PageResponse.of(page.map(e -> toDto(e, partners)));
    }

    // ---------------------------------------------------------------- write

    @Override
    @Transactional
    public CalculationResponseDto create(CalculationRequestDto req) {
        return guarded("create", () -> doCreate(req));
    }

    @SuppressWarnings("deprecation")
    private CalculationResponseDto doCreate(CalculationRequestDto req) {
        Long partnerId = tenantResolver.requirePartnerIdForCreate(req.getPartnerId());
        partnerGuard.assertUsable(partnerId);

        Calculated c = calculate(req, partnerId);
        CostCalculation e = new CostCalculation();
        e.setChannelPartnerId(partnerId);
        apply(e, req, c);
        e.setCode(codeGenerator.next("CALC", partnerId));
        CostCalculation saved = calcRepo.save(e);

        auditService.record(AuditAction.CREATE, "CALCULATOR", "CostCalculation", saved.getId(), saved.getCode(),
                partnerId, null, snapshot(saved));
        log.info("[CALC] created id={} code={} partnerId={} type={} capacityKw={} grandTotal={} {}", saved.getId(),
                saved.getCode(), partnerId, saved.getProjectType(), plain(saved.getCapacityKw()),
                plain(saved.getGrandTotal()), Actors.describe());
        return toSingleDto(saved);
    }

    @Override
    @Transactional
    public CalculationResponseDto update(Long id, CalculationRequestDto req) {
        return guarded("update", () -> doUpdate(id, req));
    }

    @SuppressWarnings("deprecation")
    private CalculationResponseDto doUpdate(Long id, CalculationRequestDto req) {
        tenantResolver.effectivePartnerId(req.getPartnerId());
        CostCalculation e = findForWrite(id);

        Calculated c = calculate(req, e.getChannelPartnerId());
        Map<String, Object> before = snapshot(e);
        apply(e, req, c);
        CostCalculation saved = calcRepo.save(e);

        Map<String, Object> after = snapshot(saved);
        List<Map<String, String>> changes = DiffUtil.diff(before, after);
        if (changes.isEmpty()) {
            log.info("[CALC] update saved, nothing changed id={} code={} partnerId={}", saved.getId(), saved.getCode(),
                    saved.getChannelPartnerId());
        } else {
            auditService.record(AuditAction.UPDATE, "CALCULATOR", "CostCalculation", saved.getId(), saved.getCode(),
                    saved.getChannelPartnerId(), before, after);
            log.info("[CALC] updated id={} code={} partnerId={} changedFields={} grandTotal={} {}", saved.getId(),
                    saved.getCode(), saved.getChannelPartnerId(), changes.size(), plain(saved.getGrandTotal()),
                    Actors.describe());
        }
        return toSingleDto(saved);
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
        CostCalculation e = findForWrite(id);
        Map<String, Object> before = snapshot(e);
        String code = e.getCode();
        Long partnerId = e.getChannelPartnerId();
        e.setDeleted(true);
        calcRepo.save(e);
        auditService.record(AuditAction.DELETE, "CALCULATOR", "CostCalculation", id, code, partnerId,
                before, null);
        log.info("[CALC] deleted id={} code={} partnerId={} {}", id, code, partnerId, Actors.describe());
    }

    // ---------------------------------------------------------------- tenant helpers

    private CostCalculation findForRead(Long id) {
        return calcRepo.findOne(id, tenantResolver.effectiveReadPartnerId(), tenantResolver.ownerFilter())
                .orElseThrow(() -> new ResourceNotFoundException("Calculation not found"));
    }

    private CostCalculation findForWrite(Long id) {
        CostCalculation e = calcRepo.findOne(id, null, tenantResolver.ownerFilter())
                .orElseThrow(() -> new ResourceNotFoundException("Calculation not found"));
        tenantResolver.assertWritable(e.getChannelPartnerId(), "Calculation", id);
        return e;
    }

    // ---------------------------------------------------------------- calculation

    private record Calculated(CostCalculator.Result result, BigDecimal gstPercent) { }

    private Calculated calculate(CalculationPreviewRequestDto r, Long partnerId) {
        BigDecimal gstPercent = r.getGstPercent() != null ? r.getGstPercent() : defaultGst(partnerId);

        CostCalculator.Input in = new CostCalculator.Input();
        in.capacityKw = r.getCapacityKw();
        in.projectType = r.getProjectType() == null ? null : r.getProjectType().name();
        in.material = r.getMaterialCost();
        in.installation = r.getInstallationCost();
        in.transportation = r.getTransportationCost();
        in.civil = r.getCivilCost();
        in.labour = r.getLabourCost();
        in.electrical = r.getElectricalCost();
        in.other = r.getOtherCost();
        in.discount = r.getDiscount();
        in.gstPercent = gstPercent;

        BigDecimal gross = CostCalculator.gross(in);
        BigDecimal discount = MoneyUtil.scale(in.discount);
        if (discount.compareTo(gross) > 0) {
            throw invalid("VALIDATION_ERROR", "Discount cannot be more than the total of the cost heads",
                    "discount above cost total partnerId=" + partnerId + " discount=" + plain(discount)
                            + " costTotal=" + plain(gross));
        }
        try {
            CostCalculator.Result result = CostCalculator.compute(in,
                    settingsProvider.savingsConfig(partnerId), settingsProvider.activeSubsidyRules(partnerId));
            return new Calculated(result, gstPercent);
        } catch (BusinessException | ResourceNotFoundException | ForbiddenException | ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[CALC] calculation error partnerId={} type={} capacityKw={} {}", partnerId, in.projectType,
                    plain(in.capacityKw), Actors.describe(), e);
            throw new ApiException(500, "Calculation failed");
        }
    }

    private BigDecimal defaultGst(Long partnerId) {
        List<GstRuleResponseDto> rules = settingsProvider.gstRules(partnerId);
        for (GstRuleResponseDto rule : rules) {
            if ("PRODUCT".equalsIgnoreCase(rule.getApplicableOn()) && rule.getGstPercent() != null) {
                return rule.getGstPercent();
            }
        }
        return FALLBACK_GST_PERCENT;
    }

    private void apply(CostCalculation e, CalculationRequestDto r, Calculated c) {
        e.setCustomerName(r.getCustomerName().trim());
        e.setProjectName(blankToNull(r.getProjectName()));
        e.setCapacityKw(r.getCapacityKw().setScale(2, RoundingMode.HALF_UP));
        e.setProjectType(r.getProjectType());
        e.setMaterialCost(MoneyUtil.scale(r.getMaterialCost()));
        e.setInstallationCost(MoneyUtil.scale(r.getInstallationCost()));
        e.setTransportationCost(MoneyUtil.scale(r.getTransportationCost()));
        e.setCivilCost(MoneyUtil.scale(r.getCivilCost()));
        e.setLabourCost(MoneyUtil.scale(r.getLabourCost()));
        e.setElectricalCost(MoneyUtil.scale(r.getElectricalCost()));
        e.setOtherCost(MoneyUtil.scale(r.getOtherCost()));
        e.setDiscount(MoneyUtil.scale(r.getDiscount()));
        e.setGstPercent(c.gstPercent().setScale(2, RoundingMode.HALF_UP));

        CostCalculator.Result res = c.result();
        e.setSubtotal(res.subtotal);
        e.setGstAmount(res.gstAmount);
        e.setGrandTotal(res.grandTotal);
        e.setCostPerKw(res.costPerKw);
        e.setEstimatedSaving(res.estimatedSaving);
        e.setSubsidyAmount(res.subsidyAmount);
        e.setNetInvestment(res.netInvestment);
    }

    // ---------------------------------------------------------------- helpers

    private BusinessException invalid(String code, String message, String logDetail) {
        log.warn("[CALC] validation: {}", logDetail);
        return new BusinessException(code, message);
    }

    private <T> T guarded(String operation, Supplier<T> body) {
        try {
            return body.get();
        } catch (BusinessException | ResourceNotFoundException | ForbiddenException | ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[CALC] {} failed unexpectedly {}", operation, Actors.describe(), e);
            throw e;
        }
    }

    private ProjectType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ProjectType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("VALIDATION_ERROR", "Unknown project type " + value, "unknown project type filter");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private Map<String, Object> snapshot(CostCalculation e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", e.getCode());
        m.put("customerName", e.getCustomerName());
        m.put("projectName", e.getProjectName());
        m.put("capacityKw", plain(e.getCapacityKw()));
        m.put("projectType", e.getProjectType() == null ? null : e.getProjectType().name());
        m.put("materialCost", plain(e.getMaterialCost()));
        m.put("installationCost", plain(e.getInstallationCost()));
        m.put("transportationCost", plain(e.getTransportationCost()));
        m.put("civilCost", plain(e.getCivilCost()));
        m.put("labourCost", plain(e.getLabourCost()));
        m.put("electricalCost", plain(e.getElectricalCost()));
        m.put("otherCost", plain(e.getOtherCost()));
        m.put("discount", plain(e.getDiscount()));
        m.put("gstPercent", plain(e.getGstPercent()));
        m.put("subtotal", plain(e.getSubtotal()));
        m.put("gstAmount", plain(e.getGstAmount()));
        m.put("grandTotal", plain(e.getGrandTotal()));
        m.put("costPerKw", plain(e.getCostPerKw()));
        m.put("estimatedSaving", plain(e.getEstimatedSaving()));
        m.put("subsidyAmount", plain(e.getSubsidyAmount()));
        m.put("netInvestment", plain(e.getNetInvestment()));
        return m;
    }

    // ---------------------------------------------------------------- response mapping

    private CalculationResponseDto toSingleDto(CostCalculation e) {
        return toDto(e, partnerRefProvider.refs(Set.of(e.getChannelPartnerId())));
    }

    private CalculationResponseDto toDto(CostCalculation e, Map<Long, PartnerRefResponseDto> partners) {
        CalculationResponseDto d = new CalculationResponseDto();
        d.setId(e.getId());
        d.setCode(e.getCode());
        d.setCustomerName(e.getCustomerName());
        d.setProjectName(e.getProjectName());
        d.setCapacityKw(e.getCapacityKw());
        d.setProjectType(e.getProjectType());
        d.setCreatedAt(e.getCreatedAt() == null ? null : e.getCreatedAt().toInstant(ZoneOffset.UTC));
        d.setPartner(partners.get(e.getChannelPartnerId()));

        CalculationResponseDto.Costs c = new CalculationResponseDto.Costs();
        c.setMaterial(e.getMaterialCost());
        c.setInstallation(e.getInstallationCost());
        c.setTransportation(e.getTransportationCost());
        c.setCivil(e.getCivilCost());
        c.setLabour(e.getLabourCost());
        c.setElectrical(e.getElectricalCost());
        c.setOther(e.getOtherCost());
        c.setDiscount(e.getDiscount());
        d.setCosts(c);

        CalculationResponseDto.Result r = new CalculationResponseDto.Result();
        r.setSubtotal(e.getSubtotal());
        r.setGstPercent(e.getGstPercent());
        r.setGstAmount(e.getGstAmount());
        r.setGrandTotal(e.getGrandTotal());
        r.setCostPerKw(e.getCostPerKw());
        r.setEstimatedSaving(MoneyUtil.nz(e.getEstimatedSaving()));
        r.setSubsidyAmount(MoneyUtil.nz(e.getSubsidyAmount()));
        r.setNetInvestment(e.getNetInvestment() != null ? e.getNetInvestment() : e.getGrandTotal());
        d.setResult(r);
        return d;
    }

    private CalculationResponseDto toPreviewDto(CalculationPreviewRequestDto r, Calculated calc, Long partnerId) {
        CostCalculator.Result res = calc.result();
        CalculationResponseDto d = new CalculationResponseDto();
        d.setCapacityKw(r.getCapacityKw());
        d.setProjectType(r.getProjectType());
        d.setCreatedAt(Instant.now());
        d.setPartner(partnerRefProvider.ref(partnerId));

        CalculationResponseDto.Costs c = new CalculationResponseDto.Costs();
        c.setMaterial(MoneyUtil.scale(r.getMaterialCost()));
        c.setInstallation(MoneyUtil.scale(r.getInstallationCost()));
        c.setTransportation(MoneyUtil.scale(r.getTransportationCost()));
        c.setCivil(MoneyUtil.scale(r.getCivilCost()));
        c.setLabour(MoneyUtil.scale(r.getLabourCost()));
        c.setElectrical(MoneyUtil.scale(r.getElectricalCost()));
        c.setOther(MoneyUtil.scale(r.getOtherCost()));
        c.setDiscount(MoneyUtil.scale(r.getDiscount()));
        d.setCosts(c);

        CalculationResponseDto.Result out = new CalculationResponseDto.Result();
        out.setSubtotal(res.subtotal);
        out.setGstPercent(calc.gstPercent());
        out.setGstAmount(res.gstAmount);
        out.setGrandTotal(res.grandTotal);
        out.setCostPerKw(res.costPerKw);
        out.setEstimatedSaving(res.estimatedSaving);
        out.setSubsidyAmount(res.subsidyAmount);
        out.setNetInvestment(res.netInvestment);
        d.setResult(out);
        return d;
    }
}