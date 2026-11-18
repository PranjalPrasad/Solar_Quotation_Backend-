package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.ProjectType;
import com.solar.Quotation.dto.requestdto.CalculationFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CalculationRequestDto;
import com.solar.Quotation.dto.responsedto.CalculationResponseDto;
import com.solar.Quotation.dto.responsedto.SavingsConfigResponseDto;
import com.solar.Quotation.entity.CostCalculation;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.CostCalculationRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.SettingsProvider;
import com.solar.Quotation.util.CodeGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Calculator tenancy, settings and audit rules, checked against an in-memory store that filters by partner id
 * the same way the real queries do (the service, TenantResolver and CurrentUser are the real classes).
 *
 *   PLATFORM (1) = Super Admin's own tenant, A (12) and B (13) = partners.
 */
class CalculatorTenantVisibilityTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;
    private static final Long B = 13L;

    private final List<CostCalculation> store = new ArrayList<>();
    private long nextId = 100;

    private CostCalculationRepository calcRepo;
    private AuditService auditService;
    private SettingsProvider settings;
    private CalculatorServiceImpl service;

    @BeforeEach
    void setUp() {
        calcRepo = mock(CostCalculationRepository.class);
        auditService = mock(AuditService.class);
        settings = mock(SettingsProvider.class);
        CodeGenerator codeGenerator = mock(CodeGenerator.class);
        when(codeGenerator.next(any(), any())).thenReturn("CALC-1001");

        // market rate differs per partner so a wrong-tenant read changes the number
        when(settings.savingsConfig(any())).thenAnswer(inv -> {
            Long partner = inv.getArgument(0);
            SavingsConfigResponseDto c = new SavingsConfigResponseDto();
            c.setMarketRatePerKw(A.equals(partner) ? new BigDecimal("60000")
                    : B.equals(partner) ? new BigDecimal("50000") : new BigDecimal("58000"));
            return c;
        });

        when(calcRepo.findOne(any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Long partner = inv.getArgument(1);
            Long owner = inv.getArgument(2);
            return store.stream()
                    .filter(c -> !c.isDeleted() && c.getId().equals(id)
                            && (partner == null || partner.equals(c.getChannelPartnerId()))
                            && (owner == null || owner.equals(c.getCreatedBy())))
                    .findFirst();
        });
        when(calcRepo.search(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Long partner = inv.getArgument(0);
            return new PageImpl<>(store.stream()
                    .filter(c -> !c.isDeleted() && (partner == null || partner.equals(c.getChannelPartnerId())))
                    .toList());
        });
        when(calcRepo.save(any(CostCalculation.class))).thenAnswer(inv -> {
            CostCalculation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(nextId++);
                store.add(c);
            }
            return c;
        });

        ChannelPartnerRepository partnerRepo = mock(ChannelPartnerRepository.class);
        when(partnerRepo.findById(any())).thenReturn(Optional.empty());

        service = new CalculatorServiceImpl(calcRepo, partnerRepo, new TenantResolver(), auditService,
                codeGenerator, settings);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private void loginAsPartner(Long partnerId, Long userId) {
        TenantContext.set(new CurrentUser(userId, "Partner user", 10L, "PARTNER_ADMIN", DataScope.PARTNER, partnerId));
    }

    private void loginAsSuperAdmin() {
        TenantContext.set(new CurrentUser(1L, "Super Admin", 1L, "SUPER_ADMIN", DataScope.ALL, PLATFORM));
    }

    private void viewing(Long viewPartnerId) {
        CurrentUser u = TenantContext.get();
        u.setReadRequest(true);
        u.setViewPartnerId(viewPartnerId);
    }

    private void writeRequestStillCarryingView(Long viewPartnerId) {
        CurrentUser u = TenantContext.get();
        u.setReadRequest(false);
        u.setViewPartnerId(viewPartnerId);
    }

    private CostCalculation seed(Long partnerId, String customer) {
        CostCalculation c = new CostCalculation();
        c.setId(nextId++);
        c.setChannelPartnerId(partnerId);
        c.setCode("CALC-" + c.getId());
        c.setCustomerName(customer);
        c.setCapacityKw(new BigDecimal("10"));
        c.setProjectType(ProjectType.RESIDENTIAL);
        c.setMaterialCost(new BigDecimal("400000"));
        c.setGstPercent(new BigDecimal("18"));
        c.setSubtotal(new BigDecimal("400000"));
        c.setGrandTotal(new BigDecimal("472000"));
        store.add(c);
        return c;
    }

    private CalculationRequestDto request(String customer) {
        CalculationRequestDto r = new CalculationRequestDto();
        r.setCustomerName(customer);
        r.setCapacityKw(new BigDecimal("10"));
        r.setProjectType(ProjectType.RESIDENTIAL);
        r.setMaterialCost(new BigDecimal("400000"));
        r.setGstPercent(new BigDecimal("18"));
        return r;
    }

    private List<Long> ids(List<CalculationResponseDto> rows) {
        return rows.stream().map(CalculationResponseDto::getId).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, String>> auditedChanges(AuditAction action) {
        ArgumentCaptor<List> changes = ArgumentCaptor.forClass(List.class);
        verify(auditService).record(eq(action), eq("CALCULATOR"), eq("CostCalculation"), any(), any(), any(), changes.capture());
        return (List<Map<String, String>>) changes.getValue();
    }

    private static Map<String, String> field(List<Map<String, String>> changes, String name) {
        return changes.stream().filter(c -> name.equals(c.get("field"))).findFirst()
                .orElseThrow(() -> new AssertionError("no audit change for field " + name + " in " + changes));
    }

    private void assertNoWriteHappened() {
        verify(calcRepo, never()).save(any(CostCalculation.class));
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ visibility

    @Test
    void calculationOfPartnerA_isInvisibleToPartnerB_inListAndGet() {
        CostCalculation a = seed(A, "A customer");
        loginAsPartner(B, 21L);

        assertTrue(service.list(new CalculationFilterRequestDto(), PageRequest.of(0, 20)).getContent().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.getById(a.getId()));
    }

    @Test
    void calculationOfPartnerA_isInvisibleToPartnerB_evenWithViewPartnerIdOrFilterPartnerId() {
        CostCalculation a = seed(A, "A customer");
        loginAsPartner(B, 21L);
        viewing(A);
        CalculationFilterRequestDto filter = new CalculationFilterRequestDto();
        filter.setPartnerId(A);

        assertTrue(service.list(filter, PageRequest.of(0, 20)).getContent().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.getById(a.getId()));
    }

    @Test
    void superAdminOwnList_excludesPartnerA_andFilterPartnerIdIsIgnored() {
        seed(A, "A customer");
        CostCalculation platform = seed(PLATFORM, "Platform customer");
        loginAsSuperAdmin();
        CalculationFilterRequestDto filter = new CalculationFilterRequestDto();
        filter.setPartnerId(A);

        assertEquals(List.of(platform.getId()), ids(service.list(filter, PageRequest.of(0, 20)).getContent()));
    }

    @Test
    void superAdmin_seesPartnerA_onlyThroughViewPartnerId_onAGet() {
        CostCalculation a = seed(A, "A customer");
        seed(PLATFORM, "Platform customer");
        loginAsSuperAdmin();
        viewing(A);

        assertEquals(List.of(a.getId()),
                ids(service.list(new CalculationFilterRequestDto(), PageRequest.of(0, 20)).getContent()));
        assertEquals(a.getId(), service.getById(a.getId()).getId());
    }

    // ------------------------------------------------------------------ writes stay in own tenant

    @Test
    void partnerB_cannotUpdateOrDeletePartnerAsCalculation() {
        CostCalculation a = seed(A, "A customer");
        loginAsPartner(B, 21L);
        writeRequestStillCarryingView(null);

        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request("Hacked")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));
        assertNoWriteHappened();
        assertEquals("A customer", a.getCustomerName());
    }

    @Test
    void superAdminViewingA_cannotWriteToA() {
        CostCalculation a = seed(A, "A customer");
        loginAsSuperAdmin();
        writeRequestStillCarryingView(A);

        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request("Hacked")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));
        assertNoWriteHappened();
    }

    @Test
    void create_ignoresClientPartnerId_andIsOwnedByCallersTenant() {
        loginAsPartner(B, 21L);
        CalculationRequestDto req = request("B customer");
        req.setPartnerId(A);

        service.create(req);

        assertEquals(B, store.get(0).getChannelPartnerId());
        verify(auditService).record(eq(AuditAction.CREATE), eq("CALCULATOR"), eq("CostCalculation"),
                any(), any(), eq(B), any());
    }

    @Test
    void ownerScope_userSeesOnlyOwnCalculations() {
        CostCalculation mine = seed(A, "Mine");
        mine.setCreatedBy(21L);
        CostCalculation theirs = seed(A, "Theirs");
        theirs.setCreatedBy(22L);
        TenantContext.set(new CurrentUser(21L, "Sales", 11L, "SALES", DataScope.OWN, A));

        assertEquals(mine.getId(), service.getById(mine.getId()).getId());
        assertThrows(ResourceNotFoundException.class, () -> service.getById(theirs.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(theirs.getId()));
    }

    // ------------------------------------------------------------------ settings come from the caller's tenant

    @Test
    void estimatedSaving_usesCallersOwnSavingsConfig() {
        loginAsPartner(A, 21L);
        CalculationResponseDto forA = service.create(request("A customer"));
        // 60000 x 10 kW = 600000 ; grand total 472000 -> saving 128000
        assertEquals(0, new BigDecimal("128000").compareTo(forA.getResult().getEstimatedSaving()));

        loginAsPartner(B, 22L);
        CalculationResponseDto forB = service.create(request("B customer"));
        // 50000 x 10 = 500000 ; 500000 - 472000 = 28000
        assertEquals(0, new BigDecimal("28000").compareTo(forB.getResult().getEstimatedSaving()));

        verify(settings).savingsConfig(A);
        verify(settings).savingsConfig(B);
        verify(settings, never()).savingsConfig(PLATFORM);
    }

    @Test
    void preview_usesCallersOwnSettings_notTheBodyPartner() {
        loginAsPartner(B, 22L);
        CalculationRequestDto req = request("B customer");
        req.setPartnerId(A);

        CalculationResponseDto out = service.preview(req);

        assertEquals(0, new BigDecimal("28000").compareTo(out.getResult().getEstimatedSaving()));
        verify(settings, never()).savingsConfig(A);
        assertNoWriteHappened();
    }

    @Test
    void estimatedSaving_neverNegative() {
        loginAsPartner(B, 22L);
        CalculationRequestDto req = request("B customer");
        req.setCapacityKw(new BigDecimal("1"));          // 50000 - 472000 < 0

        assertEquals(0, BigDecimal.ZERO.compareTo(service.preview(req).getResult().getEstimatedSaving()));
    }

    // ------------------------------------------------------------------ audit + validation

    @Test
    void update_isAuditedWithOldAndNewValues() {
        CostCalculation c = seed(A, "Old name");
        loginAsPartner(A, 21L);

        service.update(c.getId(), request("New name"));

        Map<String, String> change = field(auditedChanges(AuditAction.UPDATE), "customerName");
        assertEquals("Old name", change.get("old"));
        assertEquals("New name", change.get("new"));
    }

    @Test
    void delete_isSoftAndAuditKeepsThePreviousData() {
        CostCalculation c = seed(A, "To delete");
        loginAsPartner(A, 21L);

        service.delete(c.getId());

        assertTrue(c.isDeleted());
        Map<String, String> change = field(auditedChanges(AuditAction.DELETE), "customerName");
        assertEquals("To delete", change.get("old"));
        assertEquals(null, change.get("new"));
    }

    @Test
    void discountLargerThanCosts_isRejected_andNothingIsWritten() {
        loginAsPartner(A, 21L);
        CalculationRequestDto req = request("A customer");
        req.setDiscount(new BigDecimal("500000"));       // costs total 400000

        assertThrows(BusinessException.class, () -> service.create(req));
        assertThrows(BusinessException.class, () -> service.preview(req));
        assertNoWriteHappened();
    }
}