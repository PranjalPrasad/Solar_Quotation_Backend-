package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.GstApplicableOn;
import com.solar.Quotation.Enum.SubsidyValueType;
import com.solar.Quotation.dto.requestdto.BankDetailsRequestDto;
import com.solar.Quotation.dto.requestdto.BrandingRequestDto;
import com.solar.Quotation.dto.requestdto.GstRuleRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationDefaultsRequestDto;
import com.solar.Quotation.dto.requestdto.SavingsConfigRequestDto;
import com.solar.Quotation.dto.requestdto.SubsidyRuleRequestDto;
import com.solar.Quotation.dto.responsedto.BankDetailsResponseDto;
import com.solar.Quotation.dto.responsedto.BrandingResponseDto;
import com.solar.Quotation.dto.responsedto.GstRuleResponseDto;
import com.solar.Quotation.dto.responsedto.PublicSettingsResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;
import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;
import com.solar.Quotation.entity.GstRule;
import com.solar.Quotation.entity.PlatformSetting;
import com.solar.Quotation.entity.SubsidyRule;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.GstRuleRepository;
import com.solar.Quotation.repository.PlatformSettingRepository;
import com.solar.Quotation.repository.SubsidyRuleRepository;
import com.solar.Quotation.security.Actors;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.util.FileReferenceGuard;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.SettingsProvider;
import com.solar.Quotation.service.SettingsService;
import com.solar.Quotation.util.DiffUtil;
import com.solar.Quotation.util.SettingsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.ArrayList;

/**
 * Every settings group is PER TENANT (see SETTINGS.md): rows carry channel_partner_id and every read and write uses the
 * caller's OWN partner id (SettingsProviderImpl.scope(null)). A partner id in a request is never used, so one tenant's
 * settings never reach another, and the Super Admin's settings (PLATFORM tenant) apply to PLATFORM-owned records only.
 *
 * Permissions are checked HERE (SETTINGS:VIEW to read, SETTINGS:EDIT to change; /public needs neither), so they hold even
 * if the controller annotations are not enforced.
 *
 * Audit: every change writes an audit row (module SETTINGS, partner = the caller's tenant) with previous and new values.
 * Unchanged saves write nothing. The bank account number is masked in the audit row (****1234) but compared in full,
 * so a change that keeps the last four digits is still recorded.
 * Logging: "[SETTINGS] ..." at INFO with the group, partner id, the NAMES of the changed keys and the actor ids. Values are never logged.
 */
@Service
public class SettingsServiceImpl implements SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsServiceImpl.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** A new partner must NOT inherit the platform's identity or money details. */
    private static final Set<String> NOT_SEEDED = Set.of(SettingsProviderImpl.KEY_BRANDING, SettingsProviderImpl.KEY_BANK);
    private static final Map<String, UnaryOperator<String>> MASKED_FIELDS = Map.of("accountNumber", SettingsServiceImpl::maskTail);

    private final SettingsProvider provider;
    private final PlatformSettingRepository settingRepository;
    private final SubsidyRuleRepository subsidyRuleRepository;
    private final GstRuleRepository gstRuleRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PermissionCache permissionCache;

    public SettingsServiceImpl(SettingsProvider provider, PlatformSettingRepository settingRepository,
                               SubsidyRuleRepository subsidyRuleRepository, GstRuleRepository gstRuleRepository,
                               AuditService auditService, ObjectMapper objectMapper, PermissionCache permissionCache) {
        this.provider = provider;
        this.settingRepository = settingRepository;
        this.subsidyRuleRepository = subsidyRuleRepository;
        this.gstRuleRepository = gstRuleRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.permissionCache = permissionCache;
    }

    // ---------------------------------------------------------------- branding

    @Override
    public BrandingResponseDto getBranding() {
        require("SETTINGS:VIEW");
        return provider.branding();
    }

    @Override
    @Transactional
    public BrandingResponseDto updateBranding(BrandingRequestDto req) {
        require("SETTINGS:EDIT");
        BrandingResponseDto current = provider.branding();
        Map<String, Object> before = brandingMap(current);
        FileReferenceGuard.require(req.getLogoPath(), current.getLogoPath(), "Logo");
        BrandingResponseDto d = new BrandingResponseDto();
        d.setName(req.getName().trim());
        d.setAddress(trim(req.getAddress()));
        d.setPhone(trim(req.getPhone()));
        d.setEmail(trim(req.getEmail()));
        d.setWebsite(trim(req.getWebsite()));
        d.setLogoPath(trim(req.getLogoPath()));
        PlatformSetting row = save(SettingsProviderImpl.KEY_BRANDING, d);
        auditSetting(row, before, brandingMap(d));
        return d;
    }

    // ---------------------------------------------------------------- bank

    @Override
    public BankDetailsResponseDto getBank() {
        require("SETTINGS:VIEW");
        return provider.bank();
    }

    @Override
    @Transactional
    public BankDetailsResponseDto updateBank(BankDetailsRequestDto req) {
        require("SETTINGS:EDIT");
        Map<String, Object> before = bankMap(provider.bank());
        BankDetailsResponseDto d = new BankDetailsResponseDto();
        d.setAccountName(req.getAccountName().trim());
        d.setBankName(req.getBankName().trim());
        d.setAccountNumber(req.getAccountNumber().trim());
        d.setIfsc(req.getIfsc().trim().toUpperCase(Locale.ROOT));
        d.setBranch(trim(req.getBranch()));
        PlatformSetting row = save(SettingsProviderImpl.KEY_BANK, d);
        auditSetting(row, before, bankMap(d));
        return d;
    }

    // ---------------------------------------------------------------- quotation defaults

    @Override
    public QuotationDefaultsResponseDto getQuotationDefaults() {
        require("SETTINGS:VIEW");
        return provider.quotationDefaults();
    }

    @Override
    @Transactional
    public QuotationDefaultsResponseDto updateQuotationDefaults(QuotationDefaultsRequestDto req) {
        require("SETTINGS:EDIT");
        QuotationDefaultsRequestDto.PaymentTerms pt = req.getPaymentTerms();
        BigDecimal sum = pt.getAdvance().add(pt.getMaterial()).add(pt.getInstallation()).add(pt.getBalance());
        if (sum.compareTo(HUNDRED) != 0) {
            throw new BusinessException("VALIDATION_ERROR",
                    "Payment terms must add up to 100 percent (currently " + sum.stripTrailingZeros().toPlainString() + ")");
        }

        Map<String, Object> before = defaultsMap(provider.quotationDefaults());
        QuotationDefaultsResponseDto d = new QuotationDefaultsResponseDto();
        d.setTerms(req.getTerms().trim());
        d.setValidityDays(req.getValidityDays());
        d.setPrefix(req.getPrefix());
        QuotationDefaultsResponseDto.PaymentTerms p = new QuotationDefaultsResponseDto.PaymentTerms();
        p.setAdvance(pt.getAdvance());
        p.setMaterial(pt.getMaterial());
        p.setInstallation(pt.getInstallation());
        p.setBalance(pt.getBalance());
        d.setPaymentTerms(p);
        d.setCustomerReferralPercent(req.getCustomerReferralPercent() == null
                ? new BigDecimal("5") : req.getCustomerReferralPercent());
        PlatformSetting row = save(SettingsProviderImpl.KEY_QUOTATION_DEFAULTS, d);
        auditSetting(row, before, defaultsMap(d));
        return d;
    }

    // ---------------------------------------------------------------- savings config

    @Override
    public SavingsConfigResponseDto getSavingsConfig() {
        require("SETTINGS:VIEW");
        return provider.savingsConfig();
    }

    @Override
    @Transactional
    public SavingsConfigResponseDto updateSavingsConfig(SavingsConfigRequestDto req) {
        require("SETTINGS:EDIT");
        Map<String, Object> before = savingsMap(provider.savingsConfig());
        SavingsConfigResponseDto d = new SavingsConfigResponseDto();
        d.setUnitsPerKwPerDay(req.getUnitsPerKwPerDay());
        d.setDegradationPercent(req.getDegradationPercent());
        d.setCo2FactorKgPerKwh(req.getCo2FactorKgPerKwh());
        d.setProjectionYears(req.getProjectionYears());
        d.setMarketRatePerKw(req.getMarketRatePerKw());
        PlatformSetting row = save(SettingsProviderImpl.KEY_SAVINGS_CONFIG, d);
        auditSetting(row, before, savingsMap(d));
        return d;
    }

    // ---------------------------------------------------------------- subsidy rules

    @Override
    @Transactional(readOnly = true)
    public List<SubsidyRuleResponseDto> subsidyRules() {
        require("SETTINGS:VIEW");
        return subsidyRuleRepository.findByChannelPartnerIdAndDeletedFalseOrderBySortOrderAscIdAsc(myPartnerId()).stream()
                .map(SettingsMapper::toDto).toList();
    }

    @Override
    @Transactional
    public SubsidyRuleResponseDto createSubsidyRule(SubsidyRuleRequestDto req) {
        require("SETTINGS:EDIT");
        SubsidyRule r = new SubsidyRule();
        r.setChannelPartnerId(myPartnerId());
        applySubsidy(r, req);
        SubsidyRule saved = subsidyRuleRepository.save(r);
        record(AuditAction.CREATE, "SubsidyRule", saved.getId(), saved.getType(), null, subsidyMap(saved));
        log.info("[SETTINGS] subsidyRule created id={} partnerId={} {}", saved.getId(), saved.getChannelPartnerId(), Actors.describe());
        return SettingsMapper.toDto(saved);
    }

    @Override
    @Transactional
    public SubsidyRuleResponseDto updateSubsidyRule(Long id, SubsidyRuleRequestDto req) {
        require("SETTINGS:EDIT");
        SubsidyRule r = findSubsidy(id);
        Map<String, Object> before = subsidyMap(r);
        applySubsidy(r, req);
        SubsidyRule saved = subsidyRuleRepository.save(r);
        auditRule(AuditAction.UPDATE, "subsidyRule", "SubsidyRule", saved.getId(), saved.getType(),
                saved.getChannelPartnerId(), before, subsidyMap(saved));
        return SettingsMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteSubsidyRule(Long id) {
        require("SETTINGS:EDIT");
        SubsidyRule r = findSubsidy(id);
        r.setDeleted(true);
        subsidyRuleRepository.save(r);
        record(AuditAction.DELETE, "SubsidyRule", id, r.getType(), subsidyMap(r), null);
        log.info("[SETTINGS] subsidyRule deleted id={} partnerId={} {}", id, r.getChannelPartnerId(), Actors.describe());
    }

    // ---------------------------------------------------------------- GST rules

    @Override
    @Transactional(readOnly = true)
    public List<GstRuleResponseDto> gstRules() {
        require("SETTINGS:VIEW");
        return provider.gstRules();
    }

    @Override
    @Transactional
    public GstRuleResponseDto createGstRule(GstRuleRequestDto req) {
        require("SETTINGS:EDIT");
        GstRule r = new GstRule();
        r.setChannelPartnerId(myPartnerId());
        applyGst(r, req);
        GstRule saved = gstRuleRepository.save(r);
        record(AuditAction.CREATE, "GstRule", saved.getId(), saved.getCategory(), null, gstMap(saved));
        log.info("[SETTINGS] gstRule created id={} partnerId={} {}", saved.getId(), saved.getChannelPartnerId(), Actors.describe());
        return SettingsMapper.toDto(saved);
    }

    @Override
    @Transactional
    public GstRuleResponseDto updateGstRule(Long id, GstRuleRequestDto req) {
        require("SETTINGS:EDIT");
        GstRule r = findGst(id);
        Map<String, Object> before = gstMap(r);
        applyGst(r, req);
        GstRule saved = gstRuleRepository.save(r);
        auditRule(AuditAction.UPDATE, "gstRule", "GstRule", saved.getId(), saved.getCategory(),
                saved.getChannelPartnerId(), before, gstMap(saved));
        return SettingsMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteGstRule(Long id) {
        require("SETTINGS:EDIT");
        GstRule r = findGst(id);
        r.setDeleted(true);
        gstRuleRepository.save(r);
        record(AuditAction.DELETE, "GstRule", id, r.getCategory(), gstMap(r), null);
        log.info("[SETTINGS] gstRule deleted id={} partnerId={} {}", id, r.getChannelPartnerId(), Actors.describe());
    }

    // ---------------------------------------------------------------- public

    @Override
    @Transactional(readOnly = true)
    public PublicSettingsResponseDto publicSettings() {
        PublicSettingsResponseDto d = new PublicSettingsResponseDto();
        d.setBranding(provider.branding());
        d.setBank(provider.bank());
        d.setQuotationDefaults(provider.quotationDefaults());
        d.setSavingsConfig(provider.savingsConfig());
        d.setSubsidyRules(provider.activeSubsidyRules());
        d.setGstRules(provider.gstRules());
        return d;
    }

    // ---------------------------------------------------------------- helpers

    private PlatformSetting save(String key, Object dto) {
        PlatformSetting row = settingRepository.findByChannelPartnerIdAndSettingKeyAndDeletedFalse(myPartnerId(), key).orElseGet(() -> {
            PlatformSetting s = new PlatformSetting();
            s.setSettingKey(key);
            s.setChannelPartnerId(myPartnerId());
            return s;
        });
        row.setValueJson(objectMapper.writeValueAsString(dto));
        return settingRepository.save(row);
    }

    /** Group save (branding, bank, defaults, savings): audit + INFO log with the changed key NAMES only. Nothing changed = nothing written. */
    private void auditSetting(PlatformSetting row, Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, String>> changes = changes(before, after);
        if (changes.isEmpty()) {
            log.info("[SETTINGS] saved, nothing changed group={} partnerId={} {}",
                    row.getSettingKey(), row.getChannelPartnerId(), Actors.describe());
            return;
        }
        record(AuditAction.UPDATE, "Setting", row.getId(), row.getSettingKey(), before, after);
        log.info("[SETTINGS] updated group={} partnerId={} changedKeys={} {}",
                row.getSettingKey(), row.getChannelPartnerId(), keysOf(changes), Actors.describe());
    }

    private void auditRule(AuditAction action, String logName, String entityType, Long id, String label,
                           Long partnerId, Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, String>> changes = changes(before, after);
        if (changes.isEmpty()) {
            log.info("[SETTINGS] {} saved, nothing changed id={} partnerId={} {}", logName, id, partnerId, Actors.describe());
            return;
        }
        record(action, entityType, id, label, before, after);
        log.info("[SETTINGS] {} updated id={} partnerId={} changedKeys={} {}",
                logName, id, partnerId, keysOf(changes), Actors.describe());
    }

    private static List<String> keysOf(List<Map<String, String>> changes) {
        return changes.stream().map(c -> c.get("field")).toList();
    }

    /** Compares the REAL values, writes masked values for the fields in MASKED_FIELDS. */
    private static List<Map<String, String>> changes(Map<String, Object> before, Map<String, Object> after) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : after.entrySet()) {
            Object oldV = before == null ? null : before.get(e.getKey());
            if (Objects.equals(oldV, e.getValue())) continue;
            String o = oldV == null ? null : String.valueOf(oldV);
            String n = e.getValue() == null ? null : String.valueOf(e.getValue());
            UnaryOperator<String> mask = MASKED_FIELDS.get(e.getKey());
            if (mask != null) {
                String mo = mask.apply(o), mn = mask.apply(n);
                o = mo;
                n = Objects.equals(mo, mn) ? mn + " (changed)" : mn;   // same last digits, still a real change
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("field", e.getKey());
            row.put("old", o);
            row.put("new", n);
            out.add(row);
        }
        return out;
    }

    /** 50100234567890 -> ****7890 */
    private static String maskTail(String no) {
        if (no == null) return null;
        return "****" + (no.length() > 4 ? no.substring(no.length() - 4) : "");
    }

    private void require(String permission) {
        CurrentUser u = TenantContext.get();
        if (u == null) {
            throw new ForbiddenException("UNAUTHORIZED", "Not authenticated");
        }
        if (!permissionCache.permissionsForRole(u.getRoleId()).contains(permission)) {
            log.warn("[SETTINGS] denied, missing {} {}", permission, Actors.describe());
            throw new ForbiddenException("FORBIDDEN", "Missing permission " + permission);
        }
    }

    /** Every settings read and write is for the logged-in user's own partner. A partner id in the request is never used. */
    private Long myPartnerId() {
        return SettingsProviderImpl.scope(null);
    }

    /** The audit row is owned by the same partner whose settings changed. */
    private void record(AuditAction action, String entityType, Long id, String label,
                        Map<String, Object> before, Map<String, Object> after) {
        CurrentUser u = TenantContext.get();
        auditService.record(action, "SETTINGS", entityType, id, label, u.getPartnerId(), before, after);
    }

    private SubsidyRule findSubsidy(Long id) {
        return subsidyRuleRepository.findByIdAndChannelPartnerIdAndDeletedFalse(id, myPartnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Subsidy rule not found"));
    }

    private GstRule findGst(Long id) {
        return gstRuleRepository.findByIdAndChannelPartnerIdAndDeletedFalse(id, myPartnerId())
                .orElseThrow(() -> new ResourceNotFoundException("GST rule not found"));
    }

    private void applySubsidy(SubsidyRule r, SubsidyRuleRequestDto req) {
        SubsidyValueType valueType = SubsidyValueType.valueOf(req.getValueType());
        BigDecimal capMin = req.getCapMin() == null ? BigDecimal.ZERO : req.getCapMin();
        BigDecimal capMax = req.getCapMax();
        if (valueType == SubsidyValueType.PERCENTAGE && req.getValue().compareTo(HUNDRED) > 0) {
            throw new BusinessException("VALIDATION_ERROR", "A percentage subsidy cannot exceed 100");
        }
        if (capMax != null && capMax.compareTo(capMin) <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "Maximum kW must be greater than minimum kW");
        }
        r.setType(req.getType().trim());
        r.setCategory(req.getCategory());
        r.setValueType(valueType);
        r.setValue(req.getValue());
        r.setCapMin(capMin);
        r.setCapMax(capMax);
        r.setMaxAmount(req.getMaxAmount());
        r.setActive(req.getActive() == null || req.getActive());
        if (req.getSortOrder() != null) {
            r.setSortOrder(req.getSortOrder());
        }
    }

    private void applyGst(GstRule r, GstRuleRequestDto req) {
        r.setCategory(req.getCategory().trim());
        r.setGstPercent(req.getGstPercent());
        r.setApplicableOn(GstApplicableOn.valueOf(req.getApplicableOn()));
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String plain(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private Map<String, Object> brandingMap(BrandingResponseDto d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", d.getName());
        m.put("address", d.getAddress());
        m.put("phone", d.getPhone());
        m.put("email", d.getEmail());
        m.put("website", d.getWebsite());
        m.put("logoPath", d.getLogoPath());
        return m;
    }

    /** Raw values: the audit builder masks the account number when it writes the row. */
    private Map<String, Object> bankMap(BankDetailsResponseDto d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountName", d.getAccountName());
        m.put("bankName", d.getBankName());
        m.put("accountNumber", d.getAccountNumber());
        m.put("ifsc", d.getIfsc());
        m.put("branch", d.getBranch());
        return m;
    }

    private Map<String, Object> defaultsMap(QuotationDefaultsResponseDto d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("terms", d.getTerms());
        m.put("validityDays", String.valueOf(d.getValidityDays()));
        m.put("prefix", d.getPrefix());
        QuotationDefaultsResponseDto.PaymentTerms p = d.getPaymentTerms();
        m.put("paymentAdvance", p == null ? null : plain(p.getAdvance()));
        m.put("paymentMaterial", p == null ? null : plain(p.getMaterial()));
        m.put("paymentInstallation", p == null ? null : plain(p.getInstallation()));
        m.put("paymentBalance", p == null ? null : plain(p.getBalance()));
        m.put("customerReferralPercent", plain(d.getCustomerReferralPercent()));
        return m;
    }

    private Map<String, Object> savingsMap(SavingsConfigResponseDto d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unitsPerKwPerDay", plain(d.getUnitsPerKwPerDay()));
        m.put("degradationPercent", plain(d.getDegradationPercent()));
        m.put("co2FactorKgPerKwh", plain(d.getCo2FactorKgPerKwh()));
        m.put("projectionYears", String.valueOf(d.getProjectionYears()));
        m.put("marketRatePerKw", plain(d.getMarketRatePerKw()));
        return m;
    }

    private Map<String, Object> subsidyMap(SubsidyRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", r.getType());
        m.put("category", r.getCategory());
        m.put("valueType", r.getValueType().name());
        m.put("value", plain(r.getValue()));
        m.put("capMin", plain(r.getCapMin()));
        m.put("capMax", plain(r.getCapMax()));
        m.put("maxAmount", plain(r.getMaxAmount()));
        m.put("active", String.valueOf(r.isActive()));
        m.put("sortOrder", String.valueOf(r.getSortOrder()));
        return m;
    }

    private Map<String, Object> gstMap(GstRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", r.getCategory());
        m.put("gstPercent", plain(r.getGstPercent()));
        m.put("applicableOn", r.getApplicableOn().name());
        return m;
    }

    // ---------------------------------------------------------------- seeding a new partner

    /**
     * A new partner gets its OWN copy of the platform's business RULES (quotation defaults, savings constants, GST and
     * subsidy rules) as a starting point. Branding and bank details are NOT copied: they identify who gets paid, so a
     * partner's quotations must never carry the platform's bank account or company details. Until the partner's own
     * values are saved, bank details are empty (SettingsProviderImpl).
     * Runs with no signed-in user on purpose: the tenant guard on the entities (TenantBaseEntity) blocks a signed-in
     * Super Admin from inserting rows for another partner, and seeding is the one sanctioned exception. Flushed inside
     * that window so the guard sees no user.
     */
    @Override
    @Transactional
    public void seedForPartner(Long newPartnerId) {
        Long platformId = 1L;
        if (newPartnerId == null || newPartnerId.equals(platformId)) return;

        CurrentUser caller = TenantContext.get();
        TenantContext.clear();
        int groups = 0, subsidy = 0, gst = 0;
        try {
            for (PlatformSetting src : settingRepository.findByChannelPartnerIdAndDeletedFalse(platformId)) {
                if (NOT_SEEDED.contains(src.getSettingKey())) continue;
                PlatformSetting c = new PlatformSetting();
                c.setChannelPartnerId(newPartnerId);
                c.setSettingKey(src.getSettingKey());
                c.setValueJson(src.getValueJson());
                settingRepository.save(c);
                groups++;
            }
            for (SubsidyRule src : subsidyRuleRepository
                    .findByChannelPartnerIdAndDeletedFalseOrderBySortOrderAscIdAsc(platformId)) {
                SubsidyRule c = new SubsidyRule();
                c.setChannelPartnerId(newPartnerId);
                c.setType(src.getType());
                c.setCategory(src.getCategory());
                c.setValueType(src.getValueType());
                c.setValue(src.getValue());
                c.setCapMin(src.getCapMin());
                c.setCapMax(src.getCapMax());
                c.setMaxAmount(src.getMaxAmount());
                c.setActive(src.isActive());
                c.setSortOrder(src.getSortOrder());
                subsidyRuleRepository.save(c);
                subsidy++;
            }
            for (GstRule src : gstRuleRepository.findByChannelPartnerIdAndDeletedFalseOrderByIdAsc(platformId)) {
                GstRule c = new GstRule();
                c.setChannelPartnerId(newPartnerId);
                c.setCategory(src.getCategory());
                c.setGstPercent(src.getGstPercent());
                c.setApplicableOn(src.getApplicableOn());
                gstRuleRepository.save(c);
                gst++;
            }
            settingRepository.flush();
            subsidyRuleRepository.flush();
            gstRuleRepository.flush();
        } finally {
            if (caller != null) TenantContext.set(caller);
        }
        log.info("[SETTINGS] seeded new partner partnerId={} groups={} subsidyRules={} gstRules={} (branding and bank not copied) {}",
                newPartnerId, groups, subsidy, gst, Actors.describe());
    }
}
