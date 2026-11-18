package com.solar.Quotation.service;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.dto.requestdto.CustomerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerRequestDto;
import com.solar.Quotation.dto.requestdto.ProductFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProductRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectRequestDto;
import com.solar.Quotation.entity.Customer;
import com.solar.Quotation.entity.Product;
import com.solar.Quotation.entity.Project;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.CustomerFollowupRepository;
import com.solar.Quotation.repository.CustomerRepository;
import com.solar.Quotation.repository.ProductRepository;
import com.solar.Quotation.repository.ProjectRepository;
import com.solar.Quotation.security.CurrentUser;
import com.solar.Quotation.security.TenantContext;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.serviceImpl.CustomerServiceImpl;
import com.solar.Quotation.service.serviceImpl.ProductServiceImpl;
import com.solar.Quotation.service.serviceImpl.ProjectServiceImpl;
import com.solar.Quotation.util.AuditInfoMapper;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.PartnerGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The tenant-isolation CONTRACT of the three tenant-owned modules, checked without a database.
 *
 * Rules under test
 *   - every user, Super Admin included, creates and changes data under their OWN partner id
 *     (Super Admin: the PLATFORM partner), whatever partnerId the client sends;
 *   - reads use the caller's own partner; only data scope ALL may look at one other partner, through the
 *     view partner of a GET request (TenantContext.viewPartnerId), never through the body or the filter DTO.
 *
 * These tests assume the services call resolver.effectiveReadPartnerId() in list/get (and the write-side
 * resolver methods in create/update/delete). See TENANT_CHANGES.md for the call sites to switch.
 *
 * Still to add (needs a database, for example Testcontainers): an integration test that creates data as
 * partner A and calls every endpoint as partner B, and one where Super Admin tries to update/delete a partner's record.
 */
class TenantIsolationContractTest {

    private static final Long PLATFORM = 1L;
    private static final Long PARTNER_A = 12L;
    private static final Long OTHER_PARTNER = 99L;

    private final TenantResolver resolver = new TenantResolver();

    private CustomerRepository customerRepository;
    private ProjectRepository projectRepository;
    private ProductRepository productRepository;
    private PartnerGuard partnerGuard;

    private CustomerServiceImpl customerService;
    private ProjectServiceImpl projectService;
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        projectRepository = mock(ProjectRepository.class);
        productRepository = mock(ProductRepository.class);
        partnerGuard = mock(PartnerGuard.class);

        // Constructors are filled automatically: known objects by type, a mock for everything else.
        // So adding a new dependency to a service never breaks this test again.
        customerService = build(CustomerServiceImpl.class, customerRepository, projectRepository, resolver, partnerGuard);
        projectService = build(ProjectServiceImpl.class, projectRepository, customerRepository, resolver, partnerGuard);
        productService = build(ProductServiceImpl.class, productRepository, resolver, partnerGuard);
    }

    /** Calls the constructor with the most parameters. A parameter gets the matching object from `known`, else a mock. */
    private static <T> T build(Class<T> type, Object... known) {
        try {
            java.lang.reflect.Constructor<?> ctor = null;
            for (java.lang.reflect.Constructor<?> c : type.getDeclaredConstructors()) {
                if (ctor == null || c.getParameterCount() > ctor.getParameterCount()) ctor = c;
            }
            ctor.setAccessible(true);
            Class<?>[] types = ctor.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                for (Object k : known) {
                    if (types[i].isInstance(k)) { args[i] = k; break; }
                }
                if (args[i] == null) args[i] = mock(types[i]);
            }
            return type.cast(ctor.newInstance(args));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build " + type.getSimpleName(), e);
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    private void loginAs(DataScope scope, Long partnerId, Long userId) {
        String role = scope == DataScope.ALL ? "SUPER_ADMIN" : "ROLE_X";
        TenantContext.set(new CurrentUser(userId, "Tester", 10L, role, scope, partnerId));
    }

    /** What JwtAuthFilter does for a GET request that carries ?viewPartnerId= */
    private void viewing(Long viewPartnerId) {
        CurrentUser u = TenantContext.get();
        u.setReadRequest(true);
        u.setViewPartnerId(viewPartnerId);
    }

    // ------------------------------------------------------------------ customers

    @Test
    void customerGet_partnerUserAlwaysQueriesOwnPartner_andOtherPartnerRecordIsNotFound() {
        loginAs(DataScope.PARTNER, PARTNER_A, 5L);
        when(customerRepository.findOne(eq(77L), eq(PARTNER_A), isNull())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.get(77L));
        verify(customerRepository).findOne(77L, PARTNER_A, null);
    }

    @Test
    void customerGet_partnerUserCannotUseViewPartnerId() {
        loginAs(DataScope.PARTNER, PARTNER_A, 5L);
        viewing(OTHER_PARTNER);
        when(customerRepository.findOne(eq(77L), eq(PARTNER_A), isNull())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.get(77L));
        verify(customerRepository).findOne(77L, PARTNER_A, null);
    }

    @Test
    void customerGet_superAdminDefaultsToPlatform_andSeesOnePartnerInViewMode() {
        loginAs(DataScope.ALL, PLATFORM, 1L);
        when(customerRepository.findOne(any(), any(), any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.get(5L));
        verify(customerRepository).findOne(5L, PLATFORM, null);

        viewing(7L);
        assertThrows(ResourceNotFoundException.class, () -> customerService.get(5L));
        verify(customerRepository).findOne(5L, 7L, null);
    }

    @Test
    void customerList_partnerUserCannotChoosePartnerThroughQueryParameter() {
        loginAs(DataScope.PARTNER, PARTNER_A, 5L);
        viewing(OTHER_PARTNER);
        when(customerRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        CustomerFilterRequestDto filter = new CustomerFilterRequestDto();
        filter.setPartnerId(OTHER_PARTNER);
        customerService.list(filter);

        ArgumentCaptor<Long> partner = ArgumentCaptor.forClass(Long.class);
        verify(customerRepository).search(partner.capture(), any(), any(), any(), any(), any());
        assertEquals(PARTNER_A, partner.getValue());
    }

    @Test
    void customerList_superAdminSeesPlatformByDefault_neverAllPartners_andOnePartnerInViewMode() {
        loginAs(DataScope.ALL, PLATFORM, 1L);
        when(customerRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        customerService.list(new CustomerFilterRequestDto());
        viewing(7L);
        customerService.list(new CustomerFilterRequestDto());

        ArgumentCaptor<Long> partner = ArgumentCaptor.forClass(Long.class);
        verify(customerRepository, times(2)).search(partner.capture(), any(), any(), any(), any(), any());
        assertEquals(PLATFORM, partner.getAllValues().get(0));   // never null any more
        assertEquals(7L, partner.getAllValues().get(1));
    }

    @Test
    void customerCreate_isOwnedByTokenPartnerEvenIfBodyNamesAnotherPartner() {
        loginAs(DataScope.PARTNER, PARTNER_A, 5L);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerRequestDto req = new CustomerRequestDto();
        req.setName("Amit Sharma");
        req.setMobile("9876543210");
        req.setType("RESIDENTIAL");
        req.setPartnerId(OTHER_PARTNER);
        customerService.create(req);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertEquals(PARTNER_A, saved.getValue().getChannelPartnerId());
        verify(partnerGuard).assertUsable(PARTNER_A);
    }

    @Test
    void customerCreate_superAdminOwnsItUnderPlatform_evenWhileViewingAPartner_andIgnoresBodyPartnerId() {
        loginAs(DataScope.ALL, PLATFORM, 1L);
        viewing(7L);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerRequestDto req = new CustomerRequestDto();
        req.setName("Amit Sharma");
        req.setMobile("9876543210");
        req.setType("RESIDENTIAL");
        req.setPartnerId(OTHER_PARTNER);
        customerService.create(req);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertEquals(PLATFORM, saved.getValue().getChannelPartnerId());
        verify(partnerGuard).assertUsable(PLATFORM);
    }

    // ------------------------------------------------------------------ projects

    @Test
    void projectCreate_customerOfAnotherPartnerIsRejected() {
        loginAs(DataScope.PARTNER, PARTNER_A, 5L);
        when(customerRepository.findOne(eq(500L), eq(PARTNER_A), isNull())).thenReturn(Optional.empty());

        ProjectRequestDto req = new ProjectRequestDto();
        req.setName("Rooftop");
        req.setCustomerId(500L);
        req.setType("RESIDENTIAL");
        req.setCapacityKw(new BigDecimal("5"));

        assertThrows(BusinessException.class, () -> projectService.create(req));
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void projectCreate_superAdminCannotAttachAPartnersCustomer() {
        loginAs(DataScope.ALL, PLATFORM, 1L);
        viewing(7L);                                                   // looking at partner 7 must not widen a write
        when(customerRepository.findOne(eq(500L), eq(PLATFORM), isNull())).thenReturn(Optional.empty());

        ProjectRequestDto req = new ProjectRequestDto();
        req.setName("Rooftop");
        req.setCustomerId(500L);
        req.setType("RESIDENTIAL");
        req.setCapacityKw(new BigDecimal("5"));

        assertThrows(BusinessException.class, () -> projectService.create(req));
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void projectGet_partnerUserAlwaysQueriesOwnPartner() {
        loginAs(DataScope.PARTNER, PARTNER_A, 5L);
        when(projectRepository.findOne(eq(8L), eq(PARTNER_A), isNull())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.get(8L));
        verify(projectRepository).findOne(8L, PARTNER_A, null);
    }

    @Test
    void ownScope_addsTheCreatorFilter() {
        loginAs(DataScope.OWN, PARTNER_A, 5L);
        when(customerRepository.findOne(eq(1L), eq(PARTNER_A), eq(5L))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.get(1L));
        verify(customerRepository).findOne(1L, PARTNER_A, 5L);
    }

    // ------------------------------------------------------------------ products

    @Test
    void productGet_partnerUserAlwaysQueriesOwnPartner() {
        loginAs(DataScope.PARTNER, PARTNER_A, 5L);
        when(productRepository.findOne(eq(9L), eq(PARTNER_A))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.get(9L));
        verify(productRepository).findOne(9L, PARTNER_A);
    }

    @Test
    void productList_partnerUserCannotChoosePartnerThroughQueryParameter() {
        loginAs(DataScope.PARTNER, PARTNER_A, 5L);
        viewing(OTHER_PARTNER);
        when(productRepository.search(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenReturn(Page.empty());

        ProductFilterRequestDto filter = new ProductFilterRequestDto();
        filter.setPartnerId(OTHER_PARTNER);
        productService.list(filter);

        ArgumentCaptor<Long> partner = ArgumentCaptor.forClass(Long.class);
        verify(productRepository).search(partner.capture(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any());
        assertEquals(PARTNER_A, partner.getValue());
    }

    @Test
    void productCreate_superAdminOwnsItUnderPlatform() {
        loginAs(DataScope.ALL, PLATFORM, 1L);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductRequestDto req = new ProductRequestDto();
        req.setName("540W Panel");
        req.setCategory("SOLAR_PANEL");
        req.setUnit("Nos");
        req.setGstPercent(new BigDecimal("12"));
        req.setPrice(new BigDecimal("12500"));
        req.setStock(10);
        productService.create(req);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertEquals(PLATFORM, saved.getValue().getChannelPartnerId());
        verify(partnerGuard).assertUsable(PLATFORM);
    }
}