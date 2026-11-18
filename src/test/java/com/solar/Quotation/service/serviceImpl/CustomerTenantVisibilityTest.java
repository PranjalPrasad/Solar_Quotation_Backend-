package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.FollowupStatus;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.CustomerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerStatusRequestDto;
import com.solar.Quotation.dto.requestdto.FollowupFilterRequestDto;
import com.solar.Quotation.dto.requestdto.FollowupRequestDto;
import com.solar.Quotation.dto.responsedto.CustomerResponseDto;
import com.solar.Quotation.dto.responsedto.FollowupResponseDto;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.CustomerFollowup;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.CustomerFollowupRepository;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.ProjectRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.util.AuditInfoMapper;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.PartnerGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Customer tenant rules, checked against in-memory stores that filter exactly like the real queries do
 * (the service, TenantResolver and CurrentUser are the real classes).
 *
 *   PLATFORM (1) = Super Admin's own tenant, A (12) and B (13) = partners.
 *
 *   - By-id access across partners is a 404 on EVERY endpoint (get, update, status, delete, follow-ups, history),
 *     and nothing is changed or audited.
 *   - Super Admin sees a partner only through ?viewPartnerId= on a GET; that view is read only.
 *   - Data scope OWN: a user reaches only customers they created, and follow-ups only through such a customer.
 *   - Every write is audited under the record's own partner id with previous / new values.
 */
class CustomerTenantVisibilityTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;
    private static final Long B = 13L;

    private final List<Customer> customers = new ArrayList<>();
    private final List<CustomerFollowup> followups = new ArrayList<>();
    private long nextId = 100;

    private CustomerRepository customerRepository;
    private CustomerFollowupRepository followupRepository;
    private AuditService auditService;
    private CustomerServiceImpl service;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        followupRepository = mock(CustomerFollowupRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        auditService = mock(AuditService.class);
        PartnerGuard partnerGuard = mock(PartnerGuard.class);
        CodeGenerator codeGenerator = mock(CodeGenerator.class);
        when(codeGenerator.next(any(), any())).thenReturn("CUST-0001");

        when(customerRepository.findOne(any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Long partner = inv.getArgument(1);
            Long owner = inv.getArgument(2);
            return customers.stream().filter(c -> visible(c, partner, owner) && c.getId().equals(id)).findFirst();
        });
        when(customerRepository.search(any(), any(), any(), any(), any(), any())).thenAnswer(inv ->
                new PageImpl<>(customers.stream().filter(c -> visible(c, inv.getArgument(0), inv.getArgument(1))).toList()));
        when(customerRepository.countFiltered(any(), any(), any(), any())).thenAnswer(inv ->
                customers.stream().filter(c -> visible(c, inv.getArgument(0), inv.getArgument(1))).count());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(nextId++);
                customers.add(c);
            }
            if (c.getCreatedBy() == null && TenantContext.get() != null) {   // what @PrePersist does
                c.setCreatedBy(TenantContext.get().getUserId());
            }
            return c;
        });
        when(customerRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<Customer> out = new ArrayList<>();
            ids.forEach(i -> customers.stream().filter(c -> c.getId().equals(i)).forEach(out::add));
            return out;
        });

        when(followupRepository.findByCustomerIdAndChannelPartnerIdAndDeletedFalseOrderByFollowupDateDescIdDesc(any(), any()))
                .thenAnswer(inv -> followups.stream()
                        .filter(f -> f.getCustomerId().equals(inv.getArgument(0))
                                && f.getChannelPartnerId().equals(inv.getArgument(1))).toList());
        when(followupRepository.findByIdAndCustomerIdAndChannelPartnerIdAndDeletedFalse(any(), any(), any()))
                .thenAnswer(inv -> followups.stream()
                        .filter(f -> f.getId().equals(inv.getArgument(0))
                                && f.getCustomerId().equals(inv.getArgument(1))
                                && f.getChannelPartnerId().equals(inv.getArgument(2))).findFirst());
        when(followupRepository.save(any(CustomerFollowup.class))).thenAnswer(inv -> {
            CustomerFollowup f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(nextId++);
                followups.add(f);
            }
            return f;
        });
        // same rule as the real query: the follow-up's customer must be in the tenant (and owned, for scope OWN)
        when(followupRepository.search(any(), any(), any(), any(), anyInt(), any(), any(), any())).thenAnswer(inv -> {
            Long partner = inv.getArgument(0);
            Long owner = inv.getArgument(1);
            Long customerId = inv.getArgument(2);
            return new PageImpl<>(followups.stream()
                    .filter(f -> f.getChannelPartnerId().equals(partner))
                    .filter(f -> customers.stream().anyMatch(c -> c.getId().equals(f.getCustomerId())
                            && visible(c, partner, owner)))
                    .filter(f -> customerId == null || f.getCustomerId().equals(customerId))
                    .toList());
        });

        service = new CustomerServiceImpl(customerRepository, followupRepository, projectRepository, new TenantResolver(),
                mock(PartnerRefProvider.class), mock(AuditInfoMapper.class), auditService, codeGenerator, partnerGuard);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static boolean visible(Customer c, Long partner, Long owner) {
        return !c.isDeleted()
                && (partner == null || partner.equals(c.getChannelPartnerId()))
                && (owner == null || owner.equals(c.getCreatedBy()));
    }

    private void login(DataScope scope, Long partnerId, Long userId) {
        String role = scope == DataScope.ALL ? "SUPER_ADMIN" : "ROLE_X";
        TenantContext.set(new CurrentUser(userId, "Tester", 10L, role, scope, partnerId));
    }

    /** What JwtAuthFilter does for a GET carrying ?viewPartnerId= */
    private void viewing(Long viewPartnerId) {
        CurrentUser u = TenantContext.get();
        u.setReadRequest(true);
        u.setViewPartnerId(viewPartnerId);
    }

    private static CustomerRequestDto request(String name, String mobile) {
        CustomerRequestDto r = new CustomerRequestDto();
        r.setName(name);
        r.setMobile(mobile);
        r.setType("RESIDENTIAL");
        return r;
    }

    private static FollowupRequestDto followupRequest(String remark, LocalDate next) {
        FollowupRequestDto r = new FollowupRequestDto();
        r.setFollowupDate(LocalDate.of(2026, 11, 1));
        r.setRemark(remark);
        r.setNextDate(next);
        r.setStatus("PENDING");
        return r;
    }

    private Customer seed(Long partner, Long createdBy, String name, String mobile) {
        Customer c = new Customer();
        c.setId(nextId++);
        c.setChannelPartnerId(partner);
        c.setCreatedBy(createdBy);
        c.setCode("CUST-" + c.getId());
        c.setName(name);
        c.setMobile(mobile);
        customers.add(c);
        return c;
    }

    private CustomerFollowup seedFollowup(Customer c, String remark) {
        CustomerFollowup f = new CustomerFollowup();
        f.setId(nextId++);
        f.setChannelPartnerId(c.getChannelPartnerId());
        f.setCustomerId(c.getId());
        f.setFollowupDate(LocalDate.of(2026, 11, 1));
        f.setRemark(remark);
        f.setStatus(FollowupStatus.PENDING);
        followups.add(f);
        return f;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> auditedChanges(AuditAction action, Long partnerId) {
        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditService).record(eq(action), eq("CUSTOMER"), eq("Customer"), any(), any(), eq(partnerId), captor.capture());
        return captor.getValue();
    }

    private static Map<String, String> field(List<Map<String, String>> changes, String name) {
        return changes.stream().filter(m -> name.equals(m.get("field"))).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ cross-partner access by id

    @Test
    void partnerB_cannotReadPartnerAsCustomer_onAnyReadEndpoint() {
        Customer a = seed(A, 5L, "Asha", "9000000001");
        seedFollowup(a, "call");
        login(DataScope.PARTNER, B, 7L);

        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.followups(a.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.history(a.getId()));
        FollowupFilterRequestDto byCustomer = new FollowupFilterRequestDto();
        byCustomer.setCustomerId(a.getId());
        assertThrows(ResourceNotFoundException.class, () -> service.allFollowups(byCustomer));

        assertTrue(service.list(new CustomerFilterRequestDto()).getContent().isEmpty());
        assertEquals(0L, service.summary().getTotal());
        assertTrue(service.allFollowups(new FollowupFilterRequestDto()).getContent().isEmpty());
        verify(auditService, never()).entityHistory(any(), any());
    }

    @Test
    void partnerB_cannotChangePartnerAsCustomer_noWriteNoAudit() {
        Customer a = seed(A, 5L, "Asha", "9000000001");
        CustomerFollowup f = seedFollowup(a, "call");
        login(DataScope.PARTNER, B, 7L);

        CustomerStatusRequestDto status = new CustomerStatusRequestDto();
        status.setStatus("INACTIVE");

        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request("Hacked", "9000000009")));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.addFollowup(a.getId(), followupRequest("x", null)));
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateFollowup(a.getId(), f.getId(), followupRequest("x", null)));

        assertEquals("Asha", a.getName());
        assertEquals(RecordStatus.ACTIVE, a.getStatus());
        assertFalse(a.isDeleted());
        assertEquals("call", f.getRemark());
        verify(customerRepository, never()).save(any());
        verify(followupRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void superAdminOwnView_doesNotSeePartnerData_andCannotWriteIt() {
        Customer a = seed(A, 5L, "Asha", "9000000001");
        login(DataScope.ALL, PLATFORM, 1L);

        assertTrue(service.list(new CustomerFilterRequestDto()).getContent().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request("Hacked", "9000000009")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));
        assertEquals("Asha", a.getName());
        assertFalse(a.isDeleted());
    }

    // ------------------------------------------------------------------ view partner: read only

    @Test
    void superAdminViewingA_readsAsData_butEveryWriteIsStillRefused() {
        Customer a = seed(A, 5L, "Asha", "9000000001");
        CustomerFollowup f = seedFollowup(a, "call");
        login(DataScope.ALL, PLATFORM, 1L);
        viewing(A);

        assertEquals(1, service.list(new CustomerFilterRequestDto()).getContent().size());
        assertEquals(a.getId(), service.get(a.getId()).getId());
        assertEquals(1L, service.summary().getTotal());
        assertEquals(1, service.followups(a.getId()).size());
        assertEquals(1, service.allFollowups(new FollowupFilterRequestDto()).getContent().size());

        // the view never reaches writes, even if a write somehow carried a read-request view
        CustomerStatusRequestDto status = new CustomerStatusRequestDto();
        status.setStatus("INACTIVE");
        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request("Hacked", "9000000009")));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.addFollowup(a.getId(), followupRequest("x", null)));
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateFollowup(a.getId(), f.getId(), followupRequest("x", null)));
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void partnerUser_cannotUseViewPartnerId() {
        Customer a = seed(A, 5L, "Asha", "9000000001");
        seed(B, 7L, "Bhavna", "9000000002");
        login(DataScope.PARTNER, B, 7L);
        viewing(A);

        List<CustomerResponseDto> rows = service.list(new CustomerFilterRequestDto()).getContent();
        assertEquals(1, rows.size());
        assertEquals("Bhavna", rows.get(0).getName());
        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
    }

    @Test
    void clientPartnerIdInFilter_isIgnored() {
        seed(A, 5L, "Asha", "9000000001");
        login(DataScope.PARTNER, B, 7L);
        CustomerFilterRequestDto f = new CustomerFilterRequestDto();
        f.setPartnerId(A);
        assertTrue(service.list(f).getContent().isEmpty());
        FollowupFilterRequestDto ff = new FollowupFilterRequestDto();
        ff.setPartnerId(A);
        assertTrue(service.allFollowups(ff).getContent().isEmpty());
    }

    // ------------------------------------------------------------------ create ownership

    @Test
    void create_isOwnedByCallersTenant_whateverThePartnerIdInTheBody() {
        login(DataScope.PARTNER, B, 7L);
        CustomerRequestDto req = request("Bhavna", "9000000002");
        req.setPartnerId(A);

        CustomerResponseDto created = service.create(req);

        Customer saved = customers.stream().filter(c -> c.getId().equals(created.getId())).findFirst().orElseThrow();
        assertEquals(B, saved.getChannelPartnerId());
        auditedChanges(AuditAction.CREATE, B);
    }

    @Test
    void superAdminCreate_isOwnedByPlatform_evenWhileViewingA() {
        login(DataScope.ALL, PLATFORM, 1L);
        viewing(A);
        CustomerRequestDto req = request("Platform Co", "9000000003");
        req.setPartnerId(A);

        service.create(req);

        assertEquals(PLATFORM, customers.get(0).getChannelPartnerId());
    }

    // ------------------------------------------------------------------ data scope OWN

    @Test
    void scopeOwn_reachesOnlyOwnCustomers_andFollowupsOnlyThroughThem() {
        Customer mine = seed(A, 5L, "Mine", "9000000001");
        Customer theirs = seed(A, 6L, "Theirs", "9000000002");
        seedFollowup(mine, "mine-call");
        CustomerFollowup theirFollowup = seedFollowup(theirs, "their-call");
        login(DataScope.OWN, A, 5L);

        List<CustomerResponseDto> rows = service.list(new CustomerFilterRequestDto()).getContent();
        assertEquals(1, rows.size());
        assertEquals(mine.getId(), rows.get(0).getId());

        assertThrows(ResourceNotFoundException.class, () -> service.get(theirs.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.update(theirs.getId(), request("X", "9000000009")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(theirs.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.followups(theirs.getId()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateFollowup(theirs.getId(), theirFollowup.getId(), followupRequest("x", null)));

        List<FollowupResponseDto> visible = service.allFollowups(new FollowupFilterRequestDto()).getContent();
        assertEquals(1, visible.size());
        assertEquals("mine-call", visible.get(0).getRemark());
    }

    @Test
    void scopePartner_seesColleaguesCustomers_inSameTenant() {
        seed(A, 5L, "Mine", "9000000001");
        seed(A, 6L, "Colleague", "9000000002");
        login(DataScope.PARTNER, A, 5L);
        assertEquals(2, service.list(new CustomerFilterRequestDto()).getContent().size());
    }

    @Test
    void followupOfAnotherCustomer_cannotBeUpdatedThroughThisCustomer() {
        Customer one = seed(A, 5L, "One", "9000000001");
        Customer two = seed(A, 5L, "Two", "9000000002");
        CustomerFollowup ofTwo = seedFollowup(two, "two-call");
        login(DataScope.PARTNER, A, 5L);

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateFollowup(one.getId(), ofTwo.getId(), followupRequest("changed", null)));
        assertEquals("two-call", ofTwo.getRemark());
    }

    // ------------------------------------------------------------------ audit: previous / new data

    @Test
    void create_update_status_delete_areAudited_withPreviousAndNewValues_underTheRecordsPartner() {
        login(DataScope.PARTNER, A, 5L);
        CustomerResponseDto created = service.create(request("Asha", "9000000001"));
        Long id = created.getId();
        List<Map<String, String>> createChanges = auditedChanges(AuditAction.CREATE, A);
        assertNull(field(createChanges, "name").get("old"));
        assertEquals("Asha", field(createChanges, "name").get("new"));

        service.update(id, request("Asha Sharma", "9000000001"));
        List<Map<String, String>> updateChanges = auditedChanges(AuditAction.UPDATE, A);
        assertEquals("Asha", field(updateChanges, "name").get("old"));
        assertEquals("Asha Sharma", field(updateChanges, "name").get("new"));

        CustomerStatusRequestDto status = new CustomerStatusRequestDto();
        status.setStatus("INACTIVE");
        service.updateStatus(id, status);
        List<Map<String, String>> statusChanges = auditedChanges(AuditAction.STATUS_CHANGE, A);
        assertEquals("ACTIVE", field(statusChanges, "status").get("old"));
        assertEquals("INACTIVE", field(statusChanges, "status").get("new"));

        service.delete(id);
        List<Map<String, String>> deleteChanges = auditedChanges(AuditAction.DELETE, A);
        assertEquals("Asha Sharma", field(deleteChanges, "name").get("old"));        // previous data is kept
        assertNull(field(deleteChanges, "name").get("new"));
        assertEquals("9000000001", field(deleteChanges, "mobile").get("old"));        // not the "~del" suffixed value
    }

    @Test
    void followupAddAndStatusChange_areAudited_underTheCustomersPartner() {
        Customer c = seed(A, 5L, "Asha", "9000000001");
        login(DataScope.PARTNER, A, 5L);

        FollowupResponseDto added = service.addFollowup(c.getId(), followupRequest("first call", LocalDate.of(2026, 11, 10)));
        assertNotNull(added.getId());
        CustomerFollowup stored = followups.get(0);
        assertEquals(A, stored.getChannelPartnerId());           // inherited from the customer

        FollowupRequestDto done = followupRequest("first call", LocalDate.of(2026, 11, 10));
        done.setStatus("COMPLETED");
        service.updateFollowup(c.getId(), stored.getId(), done);

        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditService, org.mockito.Mockito.times(2))
                .record(eq(AuditAction.UPDATE), eq("CUSTOMER"), eq("Customer"), eq(c.getId()), any(), eq(A), captor.capture());
        Map<String, String> statusRow = field(captor.getAllValues().get(1), "followupStatus");
        assertEquals("PENDING", statusRow.get("old"));
        assertEquals("COMPLETED", statusRow.get("new"));
    }
}