package com.solar.Quotation.service.serviceImpl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.CustomerType;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.ProjectStatus;
import com.solar.Quotation.dto.requestdto.ProjectFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectStatusRequestDto;
import com.solar.Quotation.dto.responsedto.ProjectResponseDto;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.Project;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.ProjectRepository;
import com.solar.Quotation.repository.QuotationRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.NotificationPublisher;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.util.AuditInfoMapper;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.PartnerGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Project tenant rules (same as Customers), checked against in-memory stores that filter exactly like the real
 * queries do (the service, TenantResolver and CurrentUser are the real classes).
 *
 *   PLATFORM (1) = Super Admin's own tenant, A (12) and B (13) = partners.
 *
 *   - By-id access across partners is a 404 on EVERY endpoint (get, history, update, status, delete); nothing is
 *     changed, audited or notified.
 *   - Super Admin sees a partner only through ?viewPartnerId= on a GET; that view is read only.
 *   - A project can only be created for a customer of the caller's own tenant.
 *   - Status moves follow ProjectStatus.allowedNext(), from PATCH and from PUT; invalid ones are WARN-logged and
 *     change nothing.
 *   - Every write is audited under the record's own partner id with previous / new values.
 */
class ProjectTenantVisibilityTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;
    private static final Long B = 13L;

    private final List<Project> projects = new ArrayList<>();
    private final List<Customer> customers = new ArrayList<>();
    private long nextId = 100;

    private ProjectRepository projectRepository;
    private AuditService auditService;
    private NotificationPublisher notifications;
    private ProjectServiceImpl service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        QuotationRepository quotationRepository = mock(QuotationRepository.class);
        auditService = mock(AuditService.class);
        notifications = mock(NotificationPublisher.class);
        CodeGenerator codeGenerator = mock(CodeGenerator.class);
        when(codeGenerator.next(any(), any())).thenReturn("PRJ-0001");

        when(projectRepository.findOne(any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return projects.stream().filter(p -> visible(p, inv.getArgument(1), inv.getArgument(2))
                    && p.getId().equals(id)).findFirst();
        });
        when(projectRepository.search(any(), any(), any(), any(), any(), any(), any())).thenAnswer(inv ->
                new PageImpl<>(projects.stream().filter(p -> visible(p, inv.getArgument(0), inv.getArgument(1))).toList()));
        when(projectRepository.countFiltered(any(), any(), any(), any())).thenAnswer(inv ->
                projects.stream().filter(p -> visible(p, inv.getArgument(0), inv.getArgument(1))).count());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(nextId++);
                projects.add(p);
            }
            if (p.getCreatedBy() == null && TenantContext.get() != null) {   // what @PrePersist does
                p.setCreatedBy(TenantContext.get().getUserId());
            }
            return p;
        });

        when(customerRepository.findOne(any(), any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Long partner = inv.getArgument(1);
            Long owner = inv.getArgument(2);
            return customers.stream().filter(c -> c.getId().equals(id) && !c.isDeleted()
                    && (partner == null || partner.equals(c.getChannelPartnerId()))
                    && (owner == null || owner.equals(c.getCreatedBy()))).findFirst();
        });
        when(customerRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<Long> ids = inv.getArgument(0);
            List<Customer> out = new ArrayList<>();
            ids.forEach(i -> customers.stream().filter(c -> c.getId().equals(i)).forEach(out::add));
            return out;
        });

        service = new ProjectServiceImpl(projectRepository, customerRepository, new TenantResolver(),
                mock(PartnerRefProvider.class), mock(AuditInfoMapper.class), auditService, codeGenerator,
                quotationRepository, mock(PartnerGuard.class), notifications);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static boolean visible(Project p, Long partner, Long owner) {
        return !p.isDeleted()
                && (partner == null || partner.equals(p.getChannelPartnerId()))
                && (owner == null || owner.equals(p.getCreatedBy()));
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

    private Customer seedCustomer(Long partner, Long createdBy) {
        Customer c = new Customer();
        c.setId(nextId++);
        c.setChannelPartnerId(partner);
        c.setCreatedBy(createdBy);
        c.setCode("CUST-" + c.getId());
        c.setName("Customer " + c.getId());
        c.setMobile("90000" + c.getId());
        customers.add(c);
        return c;
    }

    private Project seed(Long partner, Long createdBy, String name, ProjectStatus status) {
        Customer c = seedCustomer(partner, createdBy);
        Project p = new Project();
        p.setId(nextId++);
        p.setChannelPartnerId(partner);
        p.setCreatedBy(createdBy);
        p.setCode("PRJ-" + p.getId());
        p.setName(name);
        p.setCustomerId(c.getId());
        p.setType(CustomerType.RESIDENTIAL);
        p.setCapacityKw(new BigDecimal("5.00"));
        p.setStatus(status);
        projects.add(p);
        return p;
    }

    private static ProjectRequestDto request(Long customerId, String name, String status) {
        ProjectRequestDto r = new ProjectRequestDto();
        r.setName(name);
        r.setCustomerId(customerId);
        r.setType("RESIDENTIAL");
        r.setCapacityKw(new BigDecimal("5"));
        r.setStatus(status);
        return r;
    }

    private static ProjectStatusRequestDto status(String s) {
        ProjectStatusRequestDto r = new ProjectStatusRequestDto();
        r.setStatus(s);
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> auditedChanges(AuditAction action, Long partnerId) {
        ArgumentCaptor<List<Map<String, String>>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditService).record(eq(action), eq("PROJECT"), eq("Project"), any(), any(), eq(partnerId), captor.capture());
        return captor.getValue();
    }

    private static Map<String, String> field(List<Map<String, String>> changes, String name) {
        return changes.stream().filter(m -> name.equals(m.get("field"))).findFirst().orElse(null);
    }

    private void verifyNothingWritten() {
        verify(projectRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
        verify(notifications, never()).projectStatusChanged(any(), any());
    }

    // ------------------------------------------------------------------ cross-partner access by id

    @Test
    void partnerB_cannotReadPartnerAsProject_onAnyReadEndpoint() {
        Project a = seed(A, 5L, "Roof A", ProjectStatus.PLANNING);
        login(DataScope.PARTNER, B, 7L);

        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.history(a.getId()));
        assertTrue(service.list(new ProjectFilterRequestDto()).getContent().isEmpty());
        assertEquals(0L, service.summary().getTotal());
        verify(auditService, never()).entityHistory(any(), any());
    }

    @Test
    void partnerB_cannotChangePartnerAsProject_noWriteNoAuditNoNotification() {
        Project a = seed(A, 5L, "Roof A", ProjectStatus.PLANNING);
        login(DataScope.PARTNER, B, 7L);

        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request(a.getCustomerId(), "Hacked", "PLANNING")));
        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request(a.getCustomerId(), "Hacked", "IN_PROGRESS")));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("IN_PROGRESS")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));

        assertEquals("Roof A", a.getName());
        assertEquals(ProjectStatus.PLANNING, a.getStatus());
        assertFalse(a.isDeleted());
        verifyNothingWritten();
    }

    @Test
    void superAdminOwnView_doesNotSeePartnerData_andCannotWriteIt() {
        Project a = seed(A, 5L, "Roof A", ProjectStatus.PLANNING);
        login(DataScope.ALL, PLATFORM, 1L);

        assertTrue(service.list(new ProjectFilterRequestDto()).getContent().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("IN_PROGRESS")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));
        assertFalse(a.isDeleted());
        verifyNothingWritten();
    }

    // ------------------------------------------------------------------ view partner: read only

    @Test
    void superAdminViewingA_readsData_butEveryWriteIsStillRefused() {
        Project a = seed(A, 5L, "Roof A", ProjectStatus.PLANNING);
        login(DataScope.ALL, PLATFORM, 1L);
        viewing(A);

        assertEquals(1, service.list(new ProjectFilterRequestDto()).getContent().size());
        assertEquals(a.getId(), service.get(a.getId()).getId());
        assertEquals(1L, service.summary().getTotal());

        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request(a.getCustomerId(), "Hacked", "PLANNING")));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status("IN_PROGRESS")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));
        assertEquals("Roof A", a.getName());
        verifyNothingWritten();
    }

    @Test
    void partnerUser_cannotUseViewPartnerId_orPartnerIdInFilter() {
        Project a = seed(A, 5L, "Roof A", ProjectStatus.PLANNING);
        seed(B, 7L, "Roof B", ProjectStatus.PLANNING);
        login(DataScope.PARTNER, B, 7L);
        viewing(A);

        List<ProjectResponseDto> rows = service.list(new ProjectFilterRequestDto()).getContent();
        assertEquals(1, rows.size());
        assertEquals("Roof B", rows.get(0).getName());
        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));

        ProjectFilterRequestDto f = new ProjectFilterRequestDto();
        f.setPartnerId(A);
        assertEquals("Roof B", service.list(f).getContent().get(0).getName());
    }

    // ------------------------------------------------------------------ create ownership

    @Test
    void create_isOwnedByCallersTenant_andStartsInPlanning() {
        login(DataScope.PARTNER, B, 7L);
        Customer mine = seedCustomer(B, 7L);

        ProjectResponseDto created = service.create(request(mine.getId(), "Roof B", "COMPLETED"));

        Project saved = projects.stream().filter(p -> p.getId().equals(created.getId())).findFirst().orElseThrow();
        assertEquals(B, saved.getChannelPartnerId());
        assertEquals(ProjectStatus.PLANNING, saved.getStatus());      // the form cannot create a project as COMPLETED
        auditedChanges(AuditAction.CREATE, B);
    }

    @Test
    void create_customerOfAnotherPartnerIsRejected_evenForSuperAdminViewingThatPartner() {
        Customer ofA = seedCustomer(A, 5L);

        login(DataScope.PARTNER, B, 7L);
        assertThrows(BusinessException.class, () -> service.create(request(ofA.getId(), "Roof", "PLANNING")));

        TenantContext.clear();
        login(DataScope.ALL, PLATFORM, 1L);
        viewing(A);                                                    // looking at A must not widen a write
        assertThrows(BusinessException.class, () -> service.create(request(ofA.getId(), "Roof", "PLANNING")));

        verify(projectRepository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_cannotMoveProjectToACustomerOfAnotherPartner() {
        Project a = seed(A, 5L, "Roof A", ProjectStatus.PLANNING);
        Customer ofB = seedCustomer(B, 7L);
        login(DataScope.PARTNER, A, 5L);

        assertThrows(BusinessException.class, () -> service.update(a.getId(), request(ofB.getId(), "Roof A", "PLANNING")));
        assertFalse(ofB.getId().equals(a.getCustomerId()));
        verifyNothingWritten();
    }

    // ------------------------------------------------------------------ data scope OWN

    @Test
    void scopeOwn_reachesOnlyOwnProjects() {
        Project mine = seed(A, 5L, "Mine", ProjectStatus.PLANNING);
        Project theirs = seed(A, 6L, "Theirs", ProjectStatus.PLANNING);
        login(DataScope.OWN, A, 5L);

        List<ProjectResponseDto> rows = service.list(new ProjectFilterRequestDto()).getContent();
        assertEquals(1, rows.size());
        assertEquals(mine.getId(), rows.get(0).getId());
        assertThrows(ResourceNotFoundException.class, () -> service.get(theirs.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(theirs.getId(), status("IN_PROGRESS")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(theirs.getId()));
        assertEquals(ProjectStatus.PLANNING, theirs.getStatus());
    }

    @Test
    void scopePartner_seesColleaguesProjects_inSameTenant() {
        seed(A, 5L, "Mine", ProjectStatus.PLANNING);
        seed(A, 6L, "Colleague", ProjectStatus.PLANNING);
        login(DataScope.PARTNER, A, 5L);
        assertEquals(2, service.list(new ProjectFilterRequestDto()).getContent().size());
    }

    // ------------------------------------------------------------------ audit: previous / new data

    @Test
    void create_update_status_delete_areAudited_withPreviousAndNewValues_underTheRecordsPartner() {
        login(DataScope.PARTNER, A, 5L);
        Customer c = seedCustomer(A, 5L);
        Long id = service.create(request(c.getId(), "Roof", "PLANNING")).getId();
        List<Map<String, String>> createChanges = auditedChanges(AuditAction.CREATE, A);
        assertNull(field(createChanges, "name").get("old"));
        assertEquals("Roof", field(createChanges, "name").get("new"));

        service.update(id, request(c.getId(), "Roof Renamed", "PLANNING"));
        List<Map<String, String>> updateChanges = auditedChanges(AuditAction.UPDATE, A);
        assertEquals("Roof", field(updateChanges, "name").get("old"));
        assertEquals("Roof Renamed", field(updateChanges, "name").get("new"));
        assertNull(field(updateChanges, "status"));                      // status never travels in an UPDATE row

        service.updateStatus(id, status("IN_PROGRESS"));
        List<Map<String, String>> statusChanges = auditedChanges(AuditAction.STATUS_CHANGE, A);
        assertEquals(1, statusChanges.size());
        assertEquals("PLANNING", field(statusChanges, "status").get("old"));
        assertEquals("IN_PROGRESS", field(statusChanges, "status").get("new"));

        service.delete(id);
        List<Map<String, String>> deleteChanges = auditedChanges(AuditAction.DELETE, A);
        assertEquals("Roof Renamed", field(deleteChanges, "name").get("old"));        // previous data is kept
        assertNull(field(deleteChanges, "name").get("new"));
    }

    @Test
    void customerChangeOnUpdate_isAuditedWithThePreviousCustomer() {
        Project a = seed(A, 5L, "Roof", ProjectStatus.PLANNING);
        Long oldCustomer = a.getCustomerId();
        Customer other = seedCustomer(A, 5L);
        login(DataScope.PARTNER, A, 5L);

        service.update(a.getId(), request(other.getId(), "Roof", "PLANNING"));

        List<Map<String, String>> changes = auditedChanges(AuditAction.UPDATE, A);
        assertEquals(String.valueOf(oldCustomer), field(changes, "customerId").get("old"));
        assertEquals(String.valueOf(other.getId()), field(changes, "customerId").get("new"));
    }

    // ------------------------------------------------------------------ status transitions

    @Test
    void validTransition_writesStatusAudit_andNotifiesWithTheProjectsPartner() {
        Project a = seed(A, 5L, "Roof", ProjectStatus.PLANNING);
        login(DataScope.PARTNER, A, 6L);

        service.updateStatus(a.getId(), status("IN_PROGRESS"));

        assertEquals(ProjectStatus.IN_PROGRESS, a.getStatus());
        verify(notifications).projectStatusChanged(
                argThat(p -> p.getId().equals(a.getId()) && A.equals(p.getChannelPartnerId())),
                eq(ProjectStatus.PLANNING));
    }

    @Test
    void invalidTransition_isRefusedWith409_changesNothing_andLogsWarn() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProjectServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Project done = seed(A, 5L, "Secret Roof", ProjectStatus.COMPLETED);   // COMPLETED is final
            login(DataScope.PARTNER, A, 5L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.updateStatus(done.getId(), status("IN_PROGRESS")));
            assertEquals("INVALID_TRANSITION", ex.getErrorCode());
            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
            assertEquals(ProjectStatus.COMPLETED, done.getStatus());
            verifyNothingWritten();

            ILoggingEvent e = appender.list.stream()
                    .filter(x -> x.getFormattedMessage().startsWith("[PROJECT] invalid status transition")).findFirst().orElseThrow();
            assertEquals(Level.WARN, e.getLevel());
            assertTrue(e.getFormattedMessage().contains("COMPLETED -> IN_PROGRESS"));
            assertFalse(e.getFormattedMessage().contains("Secret Roof"));          // ids and codes only
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void everyMoveOutsideTheTable_isRefused() {
        login(DataScope.PARTNER, A, 5L);
        for (ProjectStatus from : ProjectStatus.values()) {
            for (ProjectStatus to : ProjectStatus.values()) {
                if (from == to) continue;
                Project p = seed(A, 5L, "P", from);
                if (from.canMoveTo(to)) {
                    service.updateStatus(p.getId(), status(to.name()));
                    assertEquals(to, p.getStatus());
                } else {
                    assertThrows(BusinessException.class, () -> service.updateStatus(p.getId(), status(to.name())));
                    assertEquals(from, p.getStatus());
                }
            }
        }
    }

    @Test
    void sameStatus_writesNothing() {
        Project a = seed(A, 5L, "Roof", ProjectStatus.PLANNING);
        login(DataScope.PARTNER, A, 5L);
        service.updateStatus(a.getId(), status("PLANNING"));
        verifyNothingWritten();
    }

    @Test
    void statusThroughPut_followsTheSameRules_andIsAuditedAndNotified() {
        Project a = seed(A, 5L, "Roof", ProjectStatus.PLANNING);
        login(DataScope.PARTNER, A, 6L);

        service.update(a.getId(), request(a.getCustomerId(), "Roof 2", "IN_PROGRESS"));

        assertEquals(ProjectStatus.IN_PROGRESS, a.getStatus());
        assertEquals("Roof 2", a.getName());
        List<Map<String, String>> fieldRow = auditedChanges(AuditAction.UPDATE, A);
        assertEquals("Roof 2", field(fieldRow, "name").get("new"));
        List<Map<String, String>> statusRow = auditedChanges(AuditAction.STATUS_CHANGE, A);
        assertEquals("PLANNING", field(statusRow, "status").get("old"));
        assertEquals("IN_PROGRESS", field(statusRow, "status").get("new"));
        verify(notifications, times(1)).projectStatusChanged(any(), eq(ProjectStatus.PLANNING));
    }

    @Test
    void invalidStatusThroughPut_changesNoField_andWritesNothing() {
        Project a = seed(A, 5L, "Roof", ProjectStatus.PLANNING);
        login(DataScope.PARTNER, A, 5L);

        // PLANNING -> COMPLETED is not allowed; the renamed name in the same request must not be saved either
        assertThrows(BusinessException.class,
                () -> service.update(a.getId(), request(a.getCustomerId(), "Renamed", "COMPLETED")));

        assertEquals("Roof", a.getName());
        assertEquals(ProjectStatus.PLANNING, a.getStatus());
        verifyNothingWritten();
    }

    @Test
    void putWithoutStatusChange_doesNotNotify() {
        Project a = seed(A, 5L, "Roof", ProjectStatus.PLANNING);
        login(DataScope.PARTNER, A, 5L);
        service.update(a.getId(), request(a.getCustomerId(), "Roof 2", "PLANNING"));
        auditedChanges(AuditAction.UPDATE, A);
        verify(auditService, never()).record(eq(AuditAction.STATUS_CHANGE), any(), any(), any(), any(), any(), any());
        verify(notifications, never()).projectStatusChanged(any(), any());
    }

    // ------------------------------------------------------------------ logging

    @Test
    void writes_areLoggedAtInfo_withIdsOnly() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProjectServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            login(DataScope.PARTNER, A, 5L);
            Customer c = seedCustomer(A, 5L);
            Long id = service.create(request(c.getId(), "Private Name", "PLANNING")).getId();
            service.update(id, request(c.getId(), "Private Name 2", "PLANNING"));
            service.updateStatus(id, status("IN_PROGRESS"));

            for (String prefix : List.of("[PROJECT] created", "[PROJECT] updated", "[PROJECT] status changed")) {
                ILoggingEvent e = appender.list.stream()
                        .filter(x -> x.getFormattedMessage().startsWith(prefix)).findFirst().orElseThrow();
                assertEquals(Level.INFO, e.getLevel(), prefix);
                assertFalse(e.getFormattedMessage().contains("Private Name"), prefix);
            }
        } finally {
            logger.detachAppender(appender);
        }
    }
}