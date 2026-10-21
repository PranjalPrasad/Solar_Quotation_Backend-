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
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.GstRuleRepository;
import com.solar.Quotation.repository.PlatformSettingRepository;
import com.solar.Quotation.repository.SubsidyRuleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.SettingsProvider;
import com.solar.Quotation.service.SettingsService;
import com.solar.Quotation.util.DiffUtil;
import com.solar.Quotation.util.SettingsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SettingsServiceImpl implements SettingsService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SettingsProvider provider;
    private final PlatformSettingRepository settingRepository;
    private final SubsidyRuleRepository subsidyRuleRepository;
    private final GstRuleRepository gstRuleRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SettingsServiceImpl(SettingsProvider provider, PlatformSettingRepository settingRepository,
                               SubsidyRuleRepository subsidyRuleRepository, GstRuleRepository gstRuleRepository,
                               AuditService auditService, ObjectMapper objectMapper) {
        this.provider = provider;
        this.settingRepository = settingRepository;
        this.subsidyRuleRepository = subsidyRuleRepository;
        this.gstRuleRepository = gstRuleRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- branding

    @Override
    public BrandingResponseDto getBranding() {
        return provider.branding();
    }

    @Override
    @Transactional
    public BrandingResponseDto updateBranding(BrandingRequestDto req) {
        Map<String, Object> before = brandingMap(provider.branding());
        BrandingResponseDto d = new BrandingResponseDto();
        d.setName(req.getName().trim());
        d.setAddress(trim(req.getAddress()));
        d.setPhone(trim(req.getPhone()));
        d.setEmail(trim(req.getEmail()));
        d.setWebsite(trim(req.getWebsite()));
        d.setLogoPath(trim(req.getLogoPath()));
        PlatformSetting row = save(SettingsProviderImpl.KEY_BRANDING, d);
        audit(row, before, brandingMap(d));
        return d;
    }

    // ---------------------------------------------------------------- bank

    @Override
    public BankDetailsResponseDto getBank() {
        return provider.bank();
    }

    @Override
    @Transactional
    public BankDetailsResponseDto updateBank(BankDetailsRequestDto req) {
        Map<String, Object> before = bankMap(provider.bank());
        BankDetailsResponseDto d = new BankDetailsResponseDto();
        d.setAccountName(req.getAccountName().trim());
        d.setBankName(req.getBankName().trim());
        d.setAccountNumber(req.getAccountNumber().trim());
        d.setIfsc(req.getIfsc().trim().toUpperCase(Locale.ROOT));
        d.setBranch(trim(req.getBranch()));
        PlatformSetting row = save(SettingsProviderImpl.KEY_BANK, d);
        audit(row, before, bankMap(d));
        return d;
    }

    // ---------------------------------------------------------------- quotation defaults

    @Override
    public QuotationDefaultsResponseDto getQuotationDefaults() {
        return provider.quotationDefaults();
    }

    @Override
    @Transactional
    public QuotationDefaultsResponseDto updateQuotationDefaults(QuotationDefaultsRequestDto req) {
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
        audit(row, before, defaultsMap(d));
        return d;
    }

    // ---------------------------------------------------------------- savings config

    @Override
    public SavingsConfigResponseDto getSavingsConfig() {
        return provider.savingsConfig();
    }

    @Override
    @Transactional
    public SavingsConfigResponseDto updateSavingsConfig(SavingsConfigRequestDto req) {
        Map<String, Object> before = savingsMap(provider.savingsConfig());
        SavingsConfigResponseDto d = new SavingsConfigResponseDto();
        d.setUnitsPerKwPerDay(req.getUnitsPerKwPerDay());
        d.setDegradationPercent(req.getDegradationPercent());
        d.setCo2FactorKgPerKwh(req.getCo2FactorKgPerKwh());
        d.setProjectionYears(req.getProjectionYears());
        d.setMarketRatePerKw(req.getMarketRatePerKw());
        PlatformSetting row = save(SettingsProviderImpl.KEY_SAVINGS_CONFIG, d);
        audit(row, before, savingsMap(d));
        return d;
    }

    // ---------------------------------------------------------------- subsidy rules

    @Override
    @Transactional(readOnly = true)
    public List<SubsidyRuleResponseDto> subsidyRules() {
        return subsidyRuleRepository.findByDeletedFalseOrderBySortOrderAscIdAsc().stream()
                .map(SettingsMapper::toDto).toList();
    }

    @Override
    @Transactional
    public SubsidyRuleResponseDto createSubsidyRule(SubsidyRuleRequestDto req) {
        SubsidyRule r = new SubsidyRule();
        applySubsidy(r, req);
        SubsidyRule saved = subsidyRuleRepository.save(r);
        record(AuditAction.CREATE, "SubsidyRule", saved.getId(), saved.getType(), DiffUtil.diff(null, subsidyMap(saved)));
        return SettingsMapper.toDto(saved);
    }

    @Override
    @Transactional
    public SubsidyRuleResponseDto updateSubsidyRule(Long id, SubsidyRuleRequestDto req) {
        SubsidyRule r = findSubsidy(id);
        Map<String, Object> before = subsidyMap(r);
        applySubsidy(r, req);
        SubsidyRule saved = subsidyRuleRepository.save(r);
        record(AuditAction.UPDATE, "SubsidyRule", saved.getId(), saved.getType(), DiffUtil.diff(before, subsidyMap(saved)));
        return SettingsMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteSubsidyRule(Long id) {
        SubsidyRule r = findSubsidy(id);
        r.setDeleted(true);
        subsidyRuleRepository.save(r);
        record(AuditAction.DELETE, "SubsidyRule", id, r.getType(), null);
    }

    // ---------------------------------------------------------------- GST rules

    @Override
    @Transactional(readOnly = true)
    public List<GstRuleResponseDto> gstRules() {
        return provider.gstRules();
    }

    @Override
    @Transactional
    public GstRuleResponseDto createGstRule(GstRuleRequestDto req) {
        GstRule r = new GstRule();
        applyGst(r, req);
        GstRule saved = gstRuleRepository.save(r);
        record(AuditAction.CREATE, "GstRule", saved.getId(), saved.getCategory(), DiffUtil.diff(null, gstMap(saved)));
        return SettingsMapper.toDto(saved);
    }

    @Override
    @Transactional
    public GstRuleResponseDto updateGstRule(Long id, GstRuleRequestDto req) {
        GstRule r = findGst(id);
        Map<String, Object> before = gstMap(r);
        applyGst(r, req);
        GstRule saved = gstRuleRepository.save(r);
        record(AuditAction.UPDATE, "GstRule", saved.getId(), saved.getCategory(), DiffUtil.diff(before, gstMap(saved)));
        return SettingsMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteGstRule(Long id) {
        GstRule r = findGst(id);
        r.setDeleted(true);
        gstRuleRepository.save(r);
        record(AuditAction.DELETE, "GstRule", id, r.getCategory(), null);
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
        PlatformSetting row = settingRepository.findBySettingKeyAndDeletedFalse(key).orElseGet(() -> {
            PlatformSetting s = new PlatformSetting();
            s.setSettingKey(key);
            return s;
        });
        row.setValueJson(objectMapper.writeValueAsString(dto));
        return settingRepository.save(row);
    }

    private void audit(PlatformSetting row, Map<String, Object> before, Map<String, Object> after) {
        record(AuditAction.UPDATE, "Setting", row.getId(), row.getSettingKey(), DiffUtil.diff(before, after));
    }

    /** Settings belong to the platform, so the audit row is owned by the acting Super Admin's own partner (PLATFORM). */
    private void record(AuditAction action, String entityType, Long id, String label, List<Map<String, String>> changes) {
        CurrentUser u = TenantContext.get();
        auditService.record(action, "SETTINGS", entityType, id, label, u.getPartnerId(), changes);
    }

    private SubsidyRule findSubsidy(Long id) {
        return subsidyRuleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subsidy rule not found"));
    }

    private GstRule findGst(Long id) {
        return gstRuleRepository.findByIdAndDeletedFalse(id)
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

    /** The account number is masked in the audit trail. */
    private Map<String, Object> bankMap(BankDetailsResponseDto d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountName", d.getAccountName());
        m.put("bankName", d.getBankName());
        String no = d.getAccountNumber();
        m.put("accountNumber", no == null ? null : "****" + (no.length() > 4 ? no.substring(no.length() - 4) : no));
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
}
