package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.ReferralStatus;
import com.solar.Quotation.dto.requestdto.ReferralFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ReferralPaidRequestDto;
import com.solar.Quotation.dto.responsedto.ReferralPartnerTotalsResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralResponseDto;
import com.solar.Quotation.dto.responsedto.ReferralSummaryResponseDto;
import com.solar.Quotation.entity.Referral;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.ChannelPartnerRepository;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.QuotationRepository;
import com.solar.Quotation.repository.ReferralRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.UserNameProvider;
import com.solar.Quotation.util.AuditInfoMapper;
import com.solar.Quotation.util.CodeGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Referral tenancy, mark-paid and totals, checked against an in-memory store that filters the way the real
 * queries do (the service, TenantResolver and CurrentUser are the real classes).
 *
 *   PLATFORM (1) = Super Admin's own tenant, A (12), B (13), C (14) = partners.
 *   X: owned by A, earned by B, APPROVED, 1000.   Y: owned by PLATFORM, earned by A, APPROVED, 500.
 */
class ReferralTenantVisibilityTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;
    private static final Long B = 13L;
    private static final Long C = 14L;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    private final List<Referral> store = new ArrayList<>();
    private long nextId = 100;

    private ReferralRepository repo;
    private AuditService auditService;
    private ReferralServiceImpl service;
    private Referral x;
    private Referral y;

    @BeforeEach
    void setUp() {
        repo = mock(ReferralRepository.class);
        auditService = mock(AuditService.class);

        when(repo.findOne(any(), any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return store.stream().filter(r -> !r.isDeleted() && r.getId().equals(id)
                    && matches(r, inv.getArgument(1), inv.getArgument(2), inv.getArgument(3))).findFirst();
        });
        when(repo.search(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any())).thenAnswer(inv ->
                new PageImpl<>(store.stream().filter(r -> !r.isDeleted()
                        && matches(r, inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))).toList()));
        when(repo.statusTotals(any(), any())).thenAnswer(inv -> {
            List<Object[]> rows = new ArrayList<>();
            for (ReferralStatus st : ReferralStatus.values()) {
                List<Referral> hit = store.stream().filter(r -> !r.isDeleted() && r.getStatus() == st
                        && matches(r, inv.getArgument(0), inv.getArgument(1), null)).toList();
                if (!hit.isEmpty()) {
                    rows.add(new Object[]{st, (long) hit.size(),
                            hit.stream().map(Referral::getReferralAmount).reduce(BigDecimal.ZERO, BigDecimal::add)});
                }
            }
            return rows;
        });
        when(repo.sumPaidBetween(any(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(repo.totalsByReferrer(any(), any())).thenAnswer(inv -> {
            Long owner = inv.getArgument(0);
            Collection<Long> ids = inv.getArgument(1);
            List<Object[]> rows = new ArrayList<>();
            for (Referral r : store) {
                if (r.isDeleted() || !ids.contains(r.getReferrerPartnerId())
                        || (owner != null && !owner.equals(r.getChannelPartnerId()))) continue;
                rows.add(new Object[]{r.getReferrerPartnerId(), r.getStatus(), 1L, r.getReferralAmount()});
            }
            return rows;
        });
        when(repo.save(any(Referral.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new ReferralServiceImpl(repo, mock(QuotationRepository.class), mock(CustomerRepository.class),
                mock(PartnerRefProvider.class), mock(UserNameProvider.class), mock(AuditInfoMapper.class),
                auditService, mock(CodeGenerator.class), mock(ChannelPartnerRepository.class), new TenantResolver());

        x = seed(A, B, ReferralStatus.APPROVED, "1000");
        y = seed(PLATFORM, A, ReferralStatus.APPROVED, "500");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static boolean matches(Referral r, Long owner, Long referrer, Long viewer) {
        return (owner == null || owner.equals(r.getChannelPartnerId()))
                && (referrer == null || referrer.equals(r.getReferrerPartnerId()))
                && (viewer == null || viewer.equals(r.getChannelPartnerId()) || viewer.equals(r.getReferrerPartnerId()));
    }

    private Referral seed(Long owner, Long referrer, ReferralStatus status, String amount) {
        Referral r = new Referral();
        r.setId(nextId++);
        r.setChannelPartnerId(owner);
        r.setReferrerPartnerId(referrer);
        r.setCode("RF-" + r.getId());
        r.setQuotationId(nextId++);
        r.setCustomerId(1L);
        r.setStatus(status);
        r.setReferralAmount(new BigDecimal(amount));
        r.setQuoteDate(TODAY.minusDays(5));
        r.setAcceptedOn(TODAY.minusDays(3));
        store.add(r);
        return r;
    }

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

    private ReferralPaidRequestDto pay(String mode, String reference) {
        ReferralPaidRequestDto p = new ReferralPaidRequestDto();
        p.setPaidOn(TODAY);
        p.setPaymentMode(mode);
        p.setPaymentReference(reference);
        return p;
    }

    private List<Long> ids(List<ReferralResponseDto> rows) {
        return rows.stream().map(ReferralResponseDto::getId).toList();
    }

    private List<ReferralResponseDto> list() {
        return service.list(new ReferralFilterRequestDto()).getContent();
    }

    private void assertNoWriteHappened() {
        verify(repo, never()).save(any(Referral.class));
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ mark paid: own tenant only

    @Test
    void partnerWithoutOwnership_cannotMarkPaid() {
        loginAsPartner(C, 31L);
        writeRequestStillCarryingView(null);

        assertThrows(ResourceNotFoundException.class, () -> service.markPaid(x.getId(), pay("UPI", "REF1")));
        assertNoWriteHappened();
        assertEquals(ReferralStatus.APPROVED, x.getStatus());
    }

    @Test
    void theReferrerItself_cannotMarkItsOwnCommissionPaid() {
        loginAsPartner(B, 21L);                                   // B earned X, but A owns it

        assertThrows(ResourceNotFoundException.class, () -> service.markPaid(x.getId(), pay("UPI", "REF1")));
        assertNoWriteHappened();
    }

    @Test
    void superAdmin_cannotMarkPaidAPartnersReferral_evenWhenViewingThatPartner() {
        loginAsSuperAdmin();
        writeRequestStillCarryingView(A);

        assertThrows(ResourceNotFoundException.class, () -> service.markPaid(x.getId(), pay("UPI", "REF1")));
        assertNoWriteHappened();
    }

    @Test
    void ownerTenant_marksPaid_andItIsAuditedWithPreviousAndNewData() {
        loginAsPartner(A, 21L);

        ReferralResponseDto out = service.markPaid(x.getId(), pay("UPI", "UTR-123"));

        assertEquals("PAID", out.getStatus());
        assertEquals(ReferralStatus.PAID, x.getStatus());
        assertEquals(21L, x.getPaidBy());

        ArgumentCaptor<List> changes = ArgumentCaptor.forClass(List.class);
        verify(auditService).record(eq(AuditAction.MARK_PAID), eq("REFERRAL"), eq("Referral"),
                eq(x.getId()), any(), eq(A), changes.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rows = (List<Map<String, String>>) changes.getValue();
        Map<String, String> status = rows.stream().filter(c -> "status".equals(c.get("field"))).findFirst().orElseThrow();
        assertEquals("APPROVED", status.get("old"));
        assertEquals("PAID", status.get("new"));
        Map<String, String> mode = rows.stream().filter(c -> "paymentMode".equals(c.get("field"))).findFirst().orElseThrow();
        assertEquals(null, mode.get("old"));
        assertEquals("UPI", mode.get("new"));
    }

    @Test
    void superAdmin_marksPaidOwnTenantReferral() {
        loginAsSuperAdmin();

        service.markPaid(y.getId(), pay("CASH", null));

        assertEquals(ReferralStatus.PAID, y.getStatus());
    }

    @Test
    void markPaid_businessRules_stillApply() {
        loginAsPartner(A, 21L);

        assertThrows(BusinessException.class, () -> service.markPaid(x.getId(), pay("UPI", null)));   // reference needed
        ReferralPaidRequestDto future = pay("CASH", null);
        future.setPaidOn(TODAY.plusDays(1));
        assertThrows(BusinessException.class, () -> service.markPaid(x.getId(), future));
        x.setStatus(ReferralStatus.PENDING);
        assertThrows(BusinessException.class, () -> service.markPaid(x.getId(), pay("CASH", null)));
        assertNoWriteHappened();
    }

    // ------------------------------------------------------------------ reads

    @Test
    void unrelatedPartner_seesNothing_andGetIs404() {
        loginAsPartner(C, 31L);
        viewing(A);

        assertTrue(list().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(x.getId()));
    }

    @Test
    void partner_seesWhatItOwnsAndWhatItEarned_only() {
        loginAsPartner(A, 21L);
        assertEquals(List.of(x.getId(), y.getId()), ids(list()));      // owns X, earned Y

        loginAsPartner(B, 22L);
        assertEquals(List.of(x.getId()), ids(list()));                 // earned X
        assertThrows(ResourceNotFoundException.class, () -> service.get(y.getId()));
    }

    @Test
    void superAdminOwnList_excludesPartnerData_andPartnerIdFilterIsIgnored() {
        loginAsSuperAdmin();
        ReferralFilterRequestDto f = new ReferralFilterRequestDto();
        f.setPartnerId(A);

        assertEquals(List.of(y.getId()), ids(service.list(f).getContent()));
        assertThrows(ResourceNotFoundException.class, () -> service.get(x.getId()));
    }

    @Test
    void superAdmin_seesPartnerA_onlyThroughViewPartnerId() {
        loginAsSuperAdmin();
        viewing(A);

        assertEquals(List.of(x.getId()), ids(list()));
        assertEquals(x.getId(), service.get(x.getId()).getId());
    }

    // ------------------------------------------------------------------ totals are tenant-scoped

    @Test
    void summary_superAdminOwnTenant_countsOnlyPlatformOwnedReferrals() {
        loginAsSuperAdmin();

        ReferralSummaryResponseDto s = service.summary(A, null);          // client partnerId ignored

        assertEquals(0, new BigDecimal("500").compareTo(s.getPendingPayout()));
        assertEquals(1L, s.getApprovedCount());
    }

    @Test
    void summary_superAdminViewingA_countsOnlyAsOwnedReferrals() {
        loginAsSuperAdmin();
        viewing(A);

        ReferralSummaryResponseDto s = service.summary(null, null);

        assertEquals(0, new BigDecimal("1000").compareTo(s.getPendingPayout()));
    }

    @Test
    void summary_partnerUser_isWhatItEarned() {
        loginAsPartner(B, 22L);

        assertEquals(0, new BigDecimal("1000").compareTo(service.summary(null, null).getPendingPayout()));
    }

    @Test
    void partnerTotals_areScopedToTheReadTenant() {
        loginAsSuperAdmin();                                               // own tenant PLATFORM: X is A's
        List<ReferralPartnerTotalsResponseDto> own = service.partnerTotals(List.of(A, B));
        assertEquals(0, new BigDecimal("500").compareTo(
                own.stream().filter(t -> A.equals(t.getPartnerId())).findFirst().orElseThrow().getPendingPayout()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                own.stream().filter(t -> B.equals(t.getPartnerId())).findFirst().orElseThrow().getTotalEarned()));

        viewing(A);                                                        // A's tenant: only X, earned by B
        List<ReferralPartnerTotalsResponseDto> viewed = service.partnerTotals(List.of(A, B));
        assertEquals(0, new BigDecimal("1000").compareTo(
                viewed.stream().filter(t -> B.equals(t.getPartnerId())).findFirst().orElseThrow().getTotalEarned()));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                viewed.stream().filter(t -> A.equals(t.getPartnerId())).findFirst().orElseThrow().getTotalEarned()));
    }

    @Test
    void partnerTotalsAndHistory_areSuperAdminOnly() {
        loginAsPartner(A, 21L);

        assertThrows(ForbiddenException.class, () -> service.partnerTotals(List.of(A)));
        assertThrows(ForbiddenException.class, () -> service.history(x.getId()));
    }

    @Test
    void history_ofAnotherTenantsReferral_is404() {
        loginAsSuperAdmin();

        assertThrows(ResourceNotFoundException.class, () -> service.history(x.getId()));
        verify(auditService, never()).entityHistory(any(), any());
        assertTrue(Objects.nonNull(x));
    }
}