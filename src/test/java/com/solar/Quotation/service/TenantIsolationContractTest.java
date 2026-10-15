package com.solar.Quotation.service;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.dto.requestdto.CustomerFilterRequestDto;
import com.solar.Quotation.dto.requestdto.CustomerRequestDto;
import com.solar.Quotation.dto.requestdto.ProductFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProductRequestDto;
import com.solar.Quotation.dto.requestdto.ProjectRequestDto;
import com.solar.Quotation.entity.Customer;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The tenant-isolation CONTRACT of the three tenant-owned modules, checked without a database:
 * what partner id does each service hand to its repository for a partner user and for Super Admin?
 * If someone later changes a service so that it trusts a partner id from the request, these tests fail.
 *
 * Still to add (needs a database, for example Testcontainers): an integration test that creates data as
 * partner A and calls every endpoint as partner B, expecting an empty list or 404.
 */
class TenantIsolationContractTest {

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

        customerService = new CustomerServiceImpl(customerRepository, mock(CustomerFollowupRepository.class),
                projectRepository, resolver, mock(PartnerRefProvider.class), mock(AuditInfoMapper.class),
                mock(AuditService.class), mock(CodeGenerator.class), partnerGuard);
        projectService = new ProjectServiceImpl(projectRepository, customerRepository, resolver,
                mock(PartnerRefProvider.class), mock(AuditInfoMapper.class), mock(AuditService.class),
                mock(CodeGenerator.class));
        productService = new ProductServiceImpl(productRepository, resolver, mock(PartnerRefProvider.class),
                mock(AuditInfoMapper.class), mock(AuditService.class), mock(CodeGenerator.class), partnerGuard);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    private void loginAs(DataScope scope, Long partnerId, Long userId) {
        TenantContext.set(new CurrentUser(userId, "Tester", 10L, "ROLE_X", scope, partnerId));
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
    void customerList_partnerUserCannotChoosePartnerThroughQueryParameter() {
        loginAs(DataScope.PARTNER, PARTNER_A, 5L);
        when(customerRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        CustomerFilterRequestDto filter = new CustomerFilterRequestDto();
        filter.setPartnerId(OTHER_PARTNER);
        customerService.list(filter);

        ArgumentCaptor<Long> partner = ArgumentCaptor.forClass(Long.class);
        verify(customerRepository).search(partner.capture(), any(), any(), any(), any(), any());
        assertEquals(PARTNER_A, partner.getValue());
    }

    @Test
    void customerList_superAdminCanFilterByPartnerOrSeeAll() {
        loginAs(DataScope.ALL, 1L, 1L);
        when(customerRepository.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        CustomerFilterRequestDto filtered = new CustomerFilterRequestDto();
        filtered.setPartnerId(7L);
        customerService.list(filtered);
        customerService.list(new CustomerFilterRequestDto());

        ArgumentCaptor<Long> partner = ArgumentCaptor.forClass(Long.class);
        verify(customerRepository, org.mockito.Mockito.times(2))
                .search(partner.capture(), any(), any(), any(), any(), any());
        assertEquals(7L, partner.getAllValues().get(0));
        assertNull(partner.getAllValues().get(1));
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
    void customerCreate_superAdminMustSayWhichPartnerOwnsIt() {
        loginAs(DataScope.ALL, 1L, 1L);
        CustomerRequestDto req = new CustomerRequestDto();
        req.setName("Amit Sharma");
        req.setMobile("9876543210");
        req.setType("RESIDENTIAL");

        assertThrows(RuntimeException.class, () -> customerService.create(req));
        verify(customerRepository, never()).save(any(Customer.class));
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
    void productCreate_superAdminMustSayWhichPartnerOwnsIt() {
        loginAs(DataScope.ALL, 1L, 1L);
        ProductRequestDto req = new ProductRequestDto();
        req.setName("540W Panel");
        req.setCategory("SOLAR_PANEL");
        req.setUnit("Nos");
        req.setGstPercent(new BigDecimal("12"));
        req.setPrice(new BigDecimal("12500"));
        req.setStock(10);

        assertThrows(RuntimeException.class, () -> productService.create(req));
    }
}
