package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.dto.responsedto.BankDetailsResponseDto;
import com.solar.Quotation.dto.responsedto.BrandingResponseDto;
import com.solar.Quotation.dto.responsedto.GstRuleResponseDto;
import com.solar.Quotation.dto.responsedto.QuotationDefaultsResponseDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;
import com.solar.Quotation.dto.responsedto.SubsidyRuleResponseDto;
import com.solar.Quotation.repository.GstRuleRepository;
import com.solar.Quotation.repository.PlatformSettingRepository;
import com.solar.Quotation.repository.SubsidyRuleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.SettingsProvider;
import com.solar.Quotation.util.SettingsDefaults;
import com.solar.Quotation.util.SettingsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Supplier;

/**
 * Reads the settings of ONE partner (never another partner's, and never a shared platform copy). A group that was never saved (or whose stored JSON cannot be read) falls back to the
 * built-in defaults, so the quotation and calculator screens never break because of a missing setting.
 * Small tables, read per request. Add a short cache here only if profiling asks for it.
 */
@Component
public class SettingsProviderImpl implements SettingsProvider {

    /** The built-in PLATFORM tenant (data.sql). Used only when no user is logged in. */
    private static final Long PLATFORM_PARTNER_ID = 1L;

    public static final String KEY_BRANDING = "BRANDING";
    public static final String KEY_BANK = "BANK";
    public static final String KEY_QUOTATION_DEFAULTS = "QUOTATION_DEFAULTS";
    public static final String KEY_SAVINGS_CONFIG = "SAVINGS_CONFIG";

    private static final Logger log = LoggerFactory.getLogger(SettingsProviderImpl.class);

    private final PlatformSettingRepository settingRepository;
    private final SubsidyRuleRepository subsidyRuleRepository;
    private final GstRuleRepository gstRuleRepository;
    private final ObjectMapper objectMapper;

    public SettingsProviderImpl(PlatformSettingRepository settingRepository, SubsidyRuleRepository subsidyRuleRepository,
                                GstRuleRepository gstRuleRepository, ObjectMapper objectMapper) {
        this.settingRepository = settingRepository;
        this.subsidyRuleRepository = subsidyRuleRepository;
        this.gstRuleRepository = gstRuleRepository;
        this.objectMapper = objectMapper;
    }

    // ---- own partner (logged-in user) ----

    @Override public BrandingResponseDto branding() { return branding(null); }
    @Override public BankDetailsResponseDto bank() { return bank(null); }
    @Override public QuotationDefaultsResponseDto quotationDefaults() { return quotationDefaults(null); }
    @Override public SavingsConfigResponseDto savingsConfig() { return savingsConfig(null); }
    @Override public List<SubsidyRuleResponseDto> activeSubsidyRules() { return activeSubsidyRules(null); }
    @Override public List<GstRuleResponseDto> gstRules() { return gstRules(null); }

    // ---- explicit partner ----

    @Override
    @Transactional(readOnly = true)
    public BrandingResponseDto branding(Long partnerId) {
        return read(partnerId, KEY_BRANDING, BrandingResponseDto.class, SettingsDefaults::branding);
    }

    @Override
    @Transactional(readOnly = true)
    public BankDetailsResponseDto bank(Long partnerId) {
        return read(partnerId, KEY_BANK, BankDetailsResponseDto.class, SettingsDefaults::bank);
    }

    @Override
    @Transactional(readOnly = true)
    public QuotationDefaultsResponseDto quotationDefaults(Long partnerId) {
        return read(partnerId, KEY_QUOTATION_DEFAULTS, QuotationDefaultsResponseDto.class, SettingsDefaults::quotationDefaults);
    }

    @Override
    @Transactional(readOnly = true)
    public SavingsConfigResponseDto savingsConfig(Long partnerId) {
        return read(partnerId, KEY_SAVINGS_CONFIG, SavingsConfigResponseDto.class, SettingsDefaults::savingsConfig);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubsidyRuleResponseDto> activeSubsidyRules(Long partnerId) {
        return subsidyRuleRepository
                .findByChannelPartnerIdAndDeletedFalseAndActiveTrueOrderBySortOrderAscIdAsc(scope(partnerId)).stream()
                .map(SettingsMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GstRuleResponseDto> gstRules(Long partnerId) {
        return gstRuleRepository.findByChannelPartnerIdAndDeletedFalseOrderByIdAsc(scope(partnerId)).stream()
                .map(SettingsMapper::toDto).toList();
    }

    /** Which partner's settings to use: the one asked for, else the logged-in user's, else PLATFORM. */
    public static Long scope(Long partnerId) {
        if (partnerId != null) return partnerId;
        CurrentUser u = TenantContext.get();
        return (u != null && u.getPartnerId() != null) ? u.getPartnerId() : PLATFORM_PARTNER_ID;
    }

    private <T> T read(Long partnerId, String key, Class<T> type, Supplier<T> defaults) {
        return settingRepository.findByChannelPartnerIdAndSettingKeyAndDeletedFalse(scope(partnerId), key).map(row -> {
            try {
                return objectMapper.readValue(row.getValueJson(), type);
            } catch (RuntimeException e) {
                log.warn("Setting {} could not be read, using defaults: {}", key, e.getMessage());
                return defaults.get();
            }
        }).orElseGet(defaults);
    }
}