package com.solar.Quotation.service.serviceImpl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.dto.requestdto.BankDetailsRequestDto;
import com.solar.Quotation.dto.requestdto.BrandingRequestDto;
import com.solar.Quotation.dto.requestdto.GstRuleRequestDto;
import com.solar.Quotation.dto.requestdto.QuotationDefaultsRequestDto;
import com.solar.Quotation.dto.requestdto.SubsidyRuleRequestDto;
import com.solar.Quotation.dto.responsedto.PublicSettingsResponseDto;
import com.solar.Quotation.entity.GstRule;
import com.solar.Quotation.entity.PlatformSetting;
import com.solar.Quotation.entity.SubsidyRule;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.GstRuleRepository;
import com.solar.Quotation.repository.PlatformSettingRepository;
import com.solar.Quotation.repository.SubsidyRuleRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.PermissionCache;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Settings are per tenant. In-memory stores that filter by channel_partner_id like the real repositories; the service,
 * the provider, TenantResolver-style scoping and CurrentUser are the real classes.
 *   PLATFORM (1) = Super Admin's tenant, A (12) and B (13) = partners.
 *   Role 10 = holds SETTINGS:VIEW + SETTINGS:EDIT, role 11 = holds neither (e.g. Partner Sales).
 */
class SettingsTenantIsolationTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;
    private static final Long B = 13L;

    private final List<PlatformSetting> settingRows = new ArrayList<>();
    private final List<SubsidyRule> subsidyRows = new ArrayList<>();
    private final List<GstRule> gstRows = new ArrayList<>();
    private long nextId = 100;

    private AuditService auditService;
    private SettingsServiceImpl service;
    private SettingsProviderImpl provider;

    @BeforeEach
    void setUp() {
        PlatformSettingRepository settingRepo = mock(PlatformSettingRepository.class);
        SubsidyRuleRepository subsidyRepo = mock(SubsidyRuleRepository.class);
        GstRuleRepository gstRepo = mock(GstRuleRepository.class);
        auditService = mock(AuditService.class);

        when(settingRepo.findByChannelPartnerIdAndSettingKeyAndDeletedFalse(any(), any())).thenAnswer(inv ->
                settingRows.stream().filter(r -> !r.isDeleted() && r.getChannelPartnerId().equals(inv.getArgument(0))
                        && r.getSettingKey().equals(inv.getArgument(1))).findFirst());
        when(settingRepo.findByChannelPartnerIdAndDeletedFalse(any())).thenAnswer(inv ->
                settingRows.stream().filter(r -> !r.isDeleted() && r.getChannelPartnerId().equals(inv.getArgument(0))).toList());
        when(settingRepo.save(any(PlatformSetting.class))).thenAnswer(inv -> {
            PlatformSetting r = inv.getArgument(0);
            if (r.getId() == null) r.setId(nextId++);
            if (!settingRows.contains(r)) settingRows.add(r);
            return r;
        });

        when(subsidyRepo.findByChannelPartnerIdAndDeletedFalseOrderBySortOrderAscIdAsc(any())).thenAnswer(inv ->
                subsidyRows.stream().filter(r -> !r.isDeleted() && r.getChannelPartnerId().equals(inv.getArgument(0))).toList());
        when(subsidyRepo.findByChannelPartnerIdAndDeletedFalseAndActiveTrueOrderBySortOrderAscIdAsc(any())).thenAnswer(inv ->
                subsidyRows.stream().filter(r -> !r.isDeleted() && r.isActive() && r.getChannelPartnerId().equals(inv.getArgument(0))).toList());
        when(subsidyRepo.findByIdAndChannelPartnerIdAndDeletedFalse(any(), any())).thenAnswer(inv ->
                subsidyRows.stream().filter(r -> !r.isDeleted() && r.getId().equals(inv.getArgument(0))
                        && r.getChannelPartnerId().equals(inv.getArgument(1))).findFirst());
        when(subsidyRepo.save(any(SubsidyRule.class))).thenAnswer(inv -> {
            SubsidyRule r = inv.getArgument(0);
            if (r.getId() == null) r.setId(nextId++);
            if (!subsidyRows.contains(r)) subsidyRows.add(r);
            return r;
        });

        when(gstRepo.findByChannelPartnerIdAndDeletedFalseOrderByIdAsc(any())).thenAnswer(inv ->
                gstRows.stream().filter(r -> !r.isDeleted() && r.getChannelPartnerId().equals(inv.getArgument(0))).toList());
        when(gstRepo.findByIdAndChannelPartnerIdAndDeletedFalse(any(), any())).thenAnswer(inv ->
                gstRows.stream().filter(r -> !r.isDeleted() && r.getId().equals(inv.getArgument(0))
                        && r.getChannelPartnerId().equals(inv.getArgument(1))).findFirst());
        when(gstRepo.save(any(GstRule.class))).thenAnswer(inv -> {
            GstRule r = inv.getArgument(0);
            if (r.getId() == null) r.setId(nextId++);
            if (!gstRows.contains(r)) gstRows.add(r);
            return r;
        });

        PermissionCache permissions = mock(PermissionCache.class);
        when(permissions.permissionsForRole(10L)).thenReturn(Set.of("SETTINGS:VIEW", "SETTINGS:EDIT"));
        when(permissions.permissionsForRole(11L)).thenReturn(Set.of());

        JsonMapper mapper = JsonMapper.builder().build();
        provider = new SettingsProviderImpl(settingRepo, subsidyRepo, gstRepo, mapper);
        service = new SettingsServiceImpl(provider, settingRepo, subsidyRepo, gstRepo, auditService, mapper, permissions);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private void login(DataScope scope, Long partnerId, Long userId, Long roleId) {
        TenantContext.set(new CurrentUser(userId, "Tester", roleId, "ROLE_X", scope, partnerId));
    }

    private void asAdminOf(Long partnerId) {
        login(partnerId.equals(PLATFORM) ? DataScope.ALL : DataScope.PARTNER, partnerId, 100 + partnerId, 10L);
    }

    private static BrandingRequestDto branding(String name) {
        BrandingRequestDto r = new BrandingRequestDto();
        r.setName(name);
        r.setAddress("Street 1");
        return r;
    }

    private static BankDetailsRequestDto bank(String accountNumber) {
        BankDetailsRequestDto r = new BankDetailsRequestDto();
        r.setAccountName("Acme Solar");
        r.setBankName("Secret Bank");
        r.setAccountNumber(accountNumber);
        r.setIfsc("HDFC0001234");
        r.setBranch("Main");
        return r;
    }

    private static GstRuleRequestDto gst(String category, String percent) {
        GstRuleRequestDto r = new GstRuleRequestDto();
        r.setCategory(category);
        r.setGstPercent(new BigDecimal(percent));
        r.setApplicableOn("PRODUCT");
        return r;
    }

    private static SubsidyRuleRequestDto subsidy(String type) {
        SubsidyRuleRequestDto r = new SubsidyRuleRequestDto();
        r.setType(type);
        r.setCategory("Residential");
        r.setValueType("PERCENTAGE");
        r.setValue(new BigDecimal("10"));
        return r;
    }

    private static QuotationDefaultsRequestDto defaults(String terms) {
        QuotationDefaultsRequestDto r = new QuotationDefaultsRequestDto();
        r.setTerms(terms);
        r.setValidityDays(15);
        r.setPrefix("AB");
        QuotationDefaultsRequestDto.PaymentTerms p = new QuotationDefaultsRequestDto.PaymentTerms();
        p.setAdvance(new BigDecimal("50"));
        p.setMaterial(new BigDecimal("25"));
        p.setInstallation(new BigDecimal("15"));
        p.setBalance(new BigDecimal("10"));
        r.setPaymentTerms(p);
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> lastAudit(AuditAction action, Long partnerId) {
        ArgumentCaptor<List<Map<String, String>>> c = ArgumentCaptor.forClass(List.class);
        verify(auditService, atLeastOnce()).record(eq(action), eq("SETTINGS"), any(), any(), any(), eq(partnerId), c.capture());
        return c.getValue();
    }

    private void noAudit() {
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ tenant isolation

    @Test
    void partnerB_neverSeesPlatformOrPartnerAsSettings() {
        asAdminOf(PLATFORM);
        service.updateBranding(branding("Platform Co"));
        service.updateBank(bank("50100111122223333"));
        service.createGstRule(gst("Platform GST", "12"));
        service.createSubsidyRule(subsidy("Platform subsidy"));
        asAdminOf(A);
        service.updateBranding(branding("A Co"));
        service.updateBank(bank("90000000001111"));
        service.createGstRule(gst("A GST", "5"));

        asAdminOf(B);                                              // B saved nothing

        assertNotEquals("Platform Co", service.getBranding().getName());
        assertNotEquals("A Co", service.getBranding().getName());
        assertEquals("", service.getBank().getAccountNumber());    // never the platform's or A's account
        assertTrue(service.gstRules().isEmpty());
        assertTrue(service.subsidyRules().isEmpty());

        PublicSettingsResponseDto pub = service.publicSettings();
        assertEquals("", pub.getBank().getAccountNumber());
        assertTrue(pub.getGstRules().isEmpty());
        assertTrue(pub.getSubsidyRules().isEmpty());
    }

    @Test
    void superAdminSettings_doNotApplyToPartners_butStayOnThePlatformTenant() {
        asAdminOf(PLATFORM);
        service.updateBank(bank("50100111122223333"));
        service.createGstRule(gst("Platform GST", "12"));

        assertEquals("", provider.bank(A).getAccountNumber());
        assertTrue(provider.gstRules(A).isEmpty());
        assertEquals("50100111122223333", provider.bank(PLATFORM).getAccountNumber());
        assertEquals(1, provider.gstRules(PLATFORM).size());
    }

    @Test
    void partnerWrites_landOnThePartnersOwnRows_andLeaveOtherTenantsUntouched() {
        asAdminOf(B);
        service.updateBranding(branding("B Co"));
        asAdminOf(A);
        service.updateBranding(branding("A Co"));
        service.createGstRule(gst("A GST", "5"));

        assertEquals("A Co", provider.branding(A).getName());
        assertEquals("B Co", provider.branding(B).getName());
        assertTrue(settingRows.stream().allMatch(r -> r.getChannelPartnerId().equals(A) || r.getChannelPartnerId().equals(B)));
        assertTrue(gstRows.stream().allMatch(r -> r.getChannelPartnerId().equals(A)));
        assertTrue(provider.gstRules(B).isEmpty());
    }

    @Test
    void superAdminViewingAPartner_stillReadsAndWritesOnlyTheOwnTenant() {
        asAdminOf(A);
        service.updateBank(bank("90000000001111"));
        asAdminOf(PLATFORM);
        service.updateBank(bank("50100111122223333"));
        CurrentUser u = TenantContext.get();                       // what JwtAuthFilter does for GET ?viewPartnerId=A
        u.setReadRequest(true);
        u.setViewPartnerId(A);

        assertEquals("50100111122223333", service.getBank().getAccountNumber());   // not A's
        service.updateBank(bank("50100999988887777"));
        assertEquals("90000000001111", provider.bank(A).getAccountNumber());       // A untouched
    }

    @Test
    void partnerB_cannotChangeOrDeletePartnerAsRulesById() {
        asAdminOf(A);
        Long gstId = service.createGstRule(gst("A GST", "5")).getId();
        Long subId = service.createSubsidyRule(subsidy("A subsidy")).getId();
        clearInvocations(auditService);

        asAdminOf(B);
        assertThrows(ResourceNotFoundException.class, () -> service.updateGstRule(gstId, gst("Hacked", "99")));
        assertThrows(ResourceNotFoundException.class, () -> service.deleteGstRule(gstId));
        assertThrows(ResourceNotFoundException.class, () -> service.updateSubsidyRule(subId, subsidy("Hacked")));
        assertThrows(ResourceNotFoundException.class, () -> service.deleteSubsidyRule(subId));

        assertEquals("A GST", gstRows.get(0).getCategory());
        assertFalse(gstRows.get(0).isDeleted());
        assertFalse(subsidyRows.get(0).isDeleted());
        noAudit();
    }

    // ------------------------------------------------------------------ permissions

    @Test
    void withoutSettingsPermission_everythingButPublicIsForbidden_andNothingIsWritten() {
        login(DataScope.PARTNER, A, 7L, 11L);

        assertEquals("FORBIDDEN", assertThrows(ForbiddenException.class, () -> service.updateBank(bank("90000000001111"))).getErrorCode());
        assertThrows(ForbiddenException.class, () -> service.updateBranding(branding("X")));
        assertThrows(ForbiddenException.class, () -> service.updateQuotationDefaults(defaults("t")));
        assertThrows(ForbiddenException.class, () -> service.createGstRule(gst("X", "5")));
        assertThrows(ForbiddenException.class, () -> service.createSubsidyRule(subsidy("X")));
        assertThrows(ForbiddenException.class, () -> service.getBank());
        assertThrows(ForbiddenException.class, () -> service.gstRules());

        assertDoesNotThrow(() -> service.publicSettings());        // the quotation screen still works
        assertTrue(settingRows.isEmpty());
        assertTrue(gstRows.isEmpty());
        assertTrue(subsidyRows.isEmpty());
        noAudit();
    }

    // ------------------------------------------------------------------ audit

    @Test
    void bankChange_isAuditedWithPreviousAndNewValues_underThePartnersId_accountNumberMasked() {
        asAdminOf(A);
        service.updateBank(bank("90000000001111"));
        clearInvocations(auditService);

        service.updateBank(bank("90000000002222"));

        List<Map<String, String>> changes = lastAudit(AuditAction.UPDATE, A);
        assertEquals(1, changes.size());
        assertEquals("accountNumber", changes.get(0).get("field"));
        assertEquals("****1111", changes.get(0).get("old"));
        assertEquals("****2222", changes.get(0).get("new"));
        assertFalse(changes.toString().contains("90000000001111"));
        assertFalse(changes.toString().contains("90000000002222"));
    }

    @Test
    void accountNumberChange_thatKeepsTheLastFourDigits_isStillAudited() {
        asAdminOf(A);
        service.updateBank(bank("90000000001111"));
        clearInvocations(auditService);

        service.updateBank(bank("80000000001111"));

        List<Map<String, String>> changes = lastAudit(AuditAction.UPDATE, A);
        assertEquals("accountNumber", changes.get(0).get("field"));
        assertEquals("****1111", changes.get(0).get("old"));
        assertEquals("****1111 (changed)", changes.get(0).get("new"));
    }

    @Test
    void firstSave_auditsEveryFieldAgainstThePreviousDefaults_maskedAndPerTenant() {
        asAdminOf(A);
        service.updateBank(bank("90000000001111"));
        List<Map<String, String>> changes = lastAudit(AuditAction.UPDATE, A);
        Map<String, String> acc = changes.stream().filter(m -> "accountNumber".equals(m.get("field"))).findFirst().orElseThrow();
        assertEquals("****1111", acc.get("new"));
        assertFalse(changes.toString().contains("90000000001111"));
    }

    @Test
    void saveWithNoChange_writesNoAuditRow() {
        asAdminOf(A);
        service.updateBranding(branding("A Co"));
        clearInvocations(auditService);

        service.updateBranding(branding("A Co"));
        noAudit();
    }

    @Test
    void quotationDefaultsChange_isAuditedWithOldAndNew() {
        asAdminOf(A);
        service.updateQuotationDefaults(defaults("Old terms"));
        clearInvocations(auditService);

        service.updateQuotationDefaults(defaults("New terms"));
        List<Map<String, String>> changes = lastAudit(AuditAction.UPDATE, A);
        assertEquals(1, changes.size());
        assertEquals("terms", changes.get(0).get("field"));
        assertEquals("Old terms", changes.get(0).get("old"));
        assertEquals("New terms", changes.get(0).get("new"));
    }

    @Test
    void ruleUpdate_andDelete_keepPreviousValuesInTheAuditRow() {
        asAdminOf(A);
        Long id = service.createGstRule(gst("Panels", "5")).getId();
        clearInvocations(auditService);

        service.updateGstRule(id, gst("Panels", "12"));
        Map<String, String> upd = lastAudit(AuditAction.UPDATE, A).get(0);
        assertEquals("gstPercent", upd.get("field"));
        assertEquals("5", upd.get("old"));
        assertEquals("12", upd.get("new"));

        service.deleteGstRule(id);
        List<Map<String, String>> del = lastAudit(AuditAction.DELETE, A);
        assertTrue(del.stream().anyMatch(m -> "category".equals(m.get("field")) && "Panels".equals(m.get("old")) && m.get("new") == null));
        assertTrue(del.stream().anyMatch(m -> "gstPercent".equals(m.get("field")) && "12".equals(m.get("old"))));
    }

    // ------------------------------------------------------------------ logging

    private ListAppender<ILoggingEvent> capture(Class<?> c) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(c)).addAppender(appender);
        return appender;
    }

    private void release(Class<?> c, ListAppender<ILoggingEvent> a) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(c)).detachAppender(a);
    }

    @Test
    void change_isLoggedAtInfo_withChangedKeyNamesOnly_neverValues() {
        ListAppender<ILoggingEvent> appender = capture(SettingsServiceImpl.class);
        try {
            asAdminOf(A);
            service.updateBank(bank("90000000001111"));
            appender.list.clear();
            BankDetailsRequestDto changed = bank("90000000002222");
            changed.setBankName("Other Secret Bank");
            service.updateBank(changed);

            ILoggingEvent e = appender.list.stream()
                    .filter(x -> x.getFormattedMessage().startsWith("[SETTINGS] updated")).findFirst().orElseThrow();
            assertEquals(Level.INFO, e.getLevel());
            String m = e.getFormattedMessage();
            assertTrue(m.contains("group=BANK"));
            assertTrue(m.contains("partnerId=" + A));
            assertTrue(m.contains("accountNumber"));
            assertTrue(m.contains("bankName"));
            assertTrue(m.contains("userId=" + (100 + A)));
            assertFalse(m.contains("90000000001111"));
            assertFalse(m.contains("90000000002222"));
            assertFalse(m.contains("1111"));
            assertFalse(m.contains("Secret Bank"));
            assertFalse(m.contains("Acme"));
        } finally {
            release(SettingsServiceImpl.class, appender);
        }
    }

    @Test
    void ruleChanges_areLoggedWithIdsAndKeyNames_notRuleContents() {
        ListAppender<ILoggingEvent> appender = capture(SettingsServiceImpl.class);
        try {
            asAdminOf(A);
            Long id = service.createGstRule(gst("Confidential category", "5")).getId();
            service.updateGstRule(id, gst("Confidential category", "12"));
            service.deleteGstRule(id);

            List<String> msgs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertTrue(msgs.stream().anyMatch(x -> x.startsWith("[SETTINGS] gstRule created id=" + id)));
            assertTrue(msgs.stream().anyMatch(x -> x.startsWith("[SETTINGS] gstRule updated id=" + id) && x.contains("gstPercent")));
            assertTrue(msgs.stream().anyMatch(x -> x.startsWith("[SETTINGS] gstRule deleted id=" + id)));
            assertTrue(msgs.stream().noneMatch(x -> x.contains("Confidential")));
        } finally {
            release(SettingsServiceImpl.class, appender);
        }
    }

    @Test
    void refusedChange_isLoggedAtWarn() {
        ListAppender<ILoggingEvent> appender = capture(SettingsServiceImpl.class);
        try {
            login(DataScope.PARTNER, A, 7L, 11L);
            assertThrows(ForbiddenException.class, () -> service.updateBank(bank("90000000001111")));
            ILoggingEvent e = appender.list.get(0);
            assertEquals(Level.WARN, e.getLevel());
            assertTrue(e.getFormattedMessage().startsWith("[SETTINGS] denied"));
            assertFalse(e.getFormattedMessage().contains("90000000001111"));
        } finally {
            release(SettingsServiceImpl.class, appender);
        }
    }

    // ------------------------------------------------------------------ new partner seeding

    @Test
    void newPartner_getsRulesButNeverThePlatformsBrandingOrBankAccount() {
        asAdminOf(PLATFORM);
        service.updateBranding(branding("Platform Co"));
        service.updateBank(bank("50100111122223333"));
        service.updateQuotationDefaults(defaults("Platform terms"));
        service.createGstRule(gst("Panels", "12"));
        service.createSubsidyRule(subsidy("Central"));
        CurrentUser caller = TenantContext.get();

        service.seedForPartner(B);

        assertSame(caller, TenantContext.get());                   // the caller's context is restored
        assertEquals(1, provider.gstRules(B).size());
        assertEquals(1, provider.activeSubsidyRules(B).size());
        assertEquals("Platform terms", provider.quotationDefaults(B).getTerms());
        assertEquals("", provider.bank(B).getAccountNumber());     // not copied
        assertNotEquals("Platform Co", provider.branding(B).getName());
        assertTrue(settingRows.stream().filter(r -> r.getChannelPartnerId().equals(B))
                .noneMatch(r -> r.getSettingKey().equals("BANK") || r.getSettingKey().equals("BRANDING")));
        assertTrue(gstRows.stream().filter(r -> r.getChannelPartnerId().equals(B)).allMatch(r -> !r.getId().equals(gstRows.get(0).getId())));
    }

    @Test
    void seededCopy_isIndependent_laterChangesNeverCrossOver() {
        asAdminOf(PLATFORM);
        service.createGstRule(gst("Panels", "12"));
        service.seedForPartner(B);
        Long platformRule = gstRows.stream().filter(r -> r.getChannelPartnerId().equals(PLATFORM)).findFirst().orElseThrow().getId();

        service.updateGstRule(platformRule, gst("Panels", "18"));

        assertEquals(0, new BigDecimal("12").compareTo(provider.gstRules(B).get(0).getGstPercent()));
    }
}