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
 * Reads the settings. A group that was never saved (or whose stored JSON cannot be read) falls back to the
 * built-in defaults, so the quotation and calculator screens never break because of a missing setting.
 * Small tables, read per request. Add a short cache here only if profiling asks for it.
 */
@Component
public class SettingsProviderImpl implements SettingsProvider {

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

    @Override
    @Transactional(readOnly = true)
    public BrandingResponseDto branding() {
        return read(KEY_BRANDING, BrandingResponseDto.class, SettingsDefaults::branding);
    }

    @Override
    @Transactional(readOnly = true)
    public BankDetailsResponseDto bank() {
        return read(KEY_BANK, BankDetailsResponseDto.class, SettingsDefaults::bank);
    }

    @Override
    @Transactional(readOnly = true)
    public QuotationDefaultsResponseDto quotationDefaults() {
        return read(KEY_QUOTATION_DEFAULTS, QuotationDefaultsResponseDto.class, SettingsDefaults::quotationDefaults);
    }

    @Override
    @Transactional(readOnly = true)
    public SavingsConfigResponseDto savingsConfig() {
        return read(KEY_SAVINGS_CONFIG, SavingsConfigResponseDto.class, SettingsDefaults::savingsConfig);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubsidyRuleResponseDto> activeSubsidyRules() {
        return subsidyRuleRepository.findByDeletedFalseAndActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(SettingsMapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GstRuleResponseDto> gstRules() {
        return gstRuleRepository.findByDeletedFalseOrderByIdAsc().stream().map(SettingsMapper::toDto).toList();
    }

    private <T> T read(String key, Class<T> type, Supplier<T> defaults) {
        return settingRepository.findBySettingKeyAndDeletedFalse(key).map(row -> {
            try {
                return objectMapper.readValue(row.getValueJson(), type);
            } catch (RuntimeException e) {
                log.warn("Setting {} could not be read, using defaults: {}", key, e.getMessage());
                return defaults.get();
            }
        }).orElseGet(defaults);
    }
}
