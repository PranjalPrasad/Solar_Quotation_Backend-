package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.DataScope;
import com.solar.Quotation.Enum.ProductCategory;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.ProductFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProductRequestDto;
import com.solar.Quotation.dto.requestdto.ProductStatusRequestDto;
import com.solar.Quotation.dto.requestdto.StockAdjustRequestDto;
import com.solar.Quotation.dto.requestdto.StockMovementRequestDto;
import com.solar.Quotation.dto.responsedto.ProductCatalogItemResponseDto;
import com.solar.Quotation.dto.responsedto.ProductResponseDto;
import com.solar.Quotation.dto.responsedto.ProductSummaryResponseDto;
import com.solar.Quotation.entity.Product;
import com.solar.Quotation.entity.ProductStockMovement;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.ProductRepository;
import com.solar.Quotation.repository.ProductStockMovementRepository;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Product visibility and write rules, checked against an in-memory product store that filters by partner id
 * the same way the real queries do (the service itself, TenantResolver and CurrentUser are the real classes).
 *
 *   PLATFORM (1) = Super Admin's own tenant, A (12) and B (13) = partners.
 *
 *   - A's product is invisible to B and to Super Admin's OWN list / get.
 *   - Super Admin sees it only with ?viewPartnerId=12, and that view is read only: every write is refused.
 *   - Stock history and stock changes stay in the caller's own tenant, and every write is audited with old/new values.
 */
class ProductTenantVisibilityTest {

    private static final Long PLATFORM = 1L;
    private static final Long A = 12L;
    private static final Long B = 13L;

    private final List<Product> store = new ArrayList<>();
    private final List<ProductStockMovement> movements = new ArrayList<>();
    private long nextId = 100;

    private ProductRepository productRepository;
    private ProductStockMovementRepository movementRepository;
    private AuditService auditService;
    private PartnerGuard partnerGuard;
    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        movementRepository = mock(ProductStockMovementRepository.class);
        auditService = mock(AuditService.class);
        partnerGuard = mock(PartnerGuard.class);
        CodeGenerator codeGenerator = mock(CodeGenerator.class);
        when(codeGenerator.next(any(), any())).thenReturn("PRD-0001");

        when(productRepository.findOne(any(), any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            Long partner = inv.getArgument(1);
            return store.stream()
                    .filter(p -> !p.isDeleted() && p.getId().equals(id)
                            && (partner == null || partner.equals(p.getChannelPartnerId())))
                    .findFirst();
        });
        when(productRepository.search(any(), any(), any(), any(), anyInt(), any(), any())).thenAnswer(inv -> {
            Long partner = inv.getArgument(0);
            List<Product> rows = visible(partner);
            return new PageImpl<>(rows);
        });
        when(productRepository.findCatalog(any(), any(), any())).thenAnswer(inv -> visible(inv.getArgument(0)));
        when(productRepository.countFiltered(any(), any(), anyInt()))
                .thenAnswer(inv -> (long) visible(inv.getArgument(0)).size());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(nextId++);
                store.add(p);
            }
            return p;
        });
        when(movementRepository.save(any(ProductStockMovement.class))).thenAnswer(inv -> {
            ProductStockMovement m = inv.getArgument(0);
            movements.add(m);
            return m;
        });
        when(movementRepository.findTop200ByProductIdAndDeletedFalseOrderByCreatedAtDescIdDesc(any()))
                .thenAnswer(inv -> movements.stream().filter(m -> m.getProductId().equals(inv.getArgument(0))).toList());

        service = new ProductServiceImpl(productRepository, new TenantResolver(), mock(PartnerRefProvider.class),
                mock(AuditInfoMapper.class), auditService, codeGenerator, partnerGuard, movementRepository);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ helpers

    private List<Product> visible(Long partner) {
        return store.stream()
                .filter(p -> !p.isDeleted() && (partner == null || partner.equals(p.getChannelPartnerId())))
                .toList();
    }

    private void loginAsPartner(Long partnerId, Long userId) {
        TenantContext.set(new CurrentUser(userId, "Partner user", 10L, "PARTNER_ADMIN", DataScope.PARTNER, partnerId));
    }

    private void loginAsSuperAdmin() {
        TenantContext.set(new CurrentUser(1L, "Super Admin", 1L, "SUPER_ADMIN", DataScope.ALL, PLATFORM));
    }

    /** What JwtAuthFilter does for a GET carrying ?viewPartnerId= */
    private void viewing(Long viewPartnerId) {
        CurrentUser u = TenantContext.get();
        u.setReadRequest(true);
        u.setViewPartnerId(viewPartnerId);
    }

    /** What JwtAuthFilter does for a POST/PUT/PATCH/DELETE: the view parameter is never honoured. */
    private void writeRequestStillCarryingView(Long viewPartnerId) {
        CurrentUser u = TenantContext.get();
        u.setReadRequest(false);
        u.setViewPartnerId(viewPartnerId);
    }

    private Product seed(Long partnerId, String name, int stock) {
        Product p = new Product();
        p.setId(nextId++);
        p.setChannelPartnerId(partnerId);
        p.setCode("PRD-" + p.getId());
        p.setName(name);
        p.setCategory(ProductCategory.SOLAR_PANEL);
        p.setUnit("Nos");
        p.setGstPercent(new BigDecimal("12"));
        p.setPrice(new BigDecimal("12500.00"));
        p.setStock(stock);
        p.setStatus(RecordStatus.ACTIVE);
        store.add(p);
        return p;
    }

    private ProductRequestDto request(String name) {
        ProductRequestDto req = new ProductRequestDto();
        req.setName(name);
        req.setCategory("SOLAR_PANEL");
        req.setUnit("Nos");
        req.setGstPercent(new BigDecimal("12"));
        req.setPrice(new BigDecimal("12500"));
        req.setStock(10);
        return req;
    }

    private StockAdjustRequestDto adjust(int by, String reason) {
        StockAdjustRequestDto a = new StockAdjustRequestDto();
        a.setAdjustment(by);
        a.setReason(reason);
        return a;
    }

    private List<Long> ids(List<ProductResponseDto> rows) {
        return rows.stream().map(ProductResponseDto::getId).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, String>> auditedChanges(AuditAction action) {
        ArgumentCaptor<List> changes = ArgumentCaptor.forClass(List.class);
        verify(auditService).record(eq(action), eq("PRODUCT"), eq("Product"), any(), any(), any(), changes.capture());
        return (List<Map<String, String>>) changes.getValue();
    }

    private static Map<String, String> field(List<Map<String, String>> changes, String name) {
        return changes.stream().filter(c -> name.equals(c.get("field"))).findFirst()
                .orElseThrow(() -> new AssertionError("no audit change for field " + name + " in " + changes));
    }

    private void assertNoWriteHappened() {
        verify(productRepository, never()).save(any(Product.class));
        verify(movementRepository, never()).save(any(ProductStockMovement.class));
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ visibility

    @Test
    void productOfPartnerA_isInvisibleToPartnerB_inListGetCatalogAndSummary() {
        Product a = seed(A, "A panel", 5);
        loginAsPartner(B, 21L);

        assertTrue(service.list(new ProductFilterRequestDto()).getContent().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
        assertTrue(service.catalog().isEmpty());
        assertEquals(0L, service.summary().getTotal());
    }

    @Test
    void productOfPartnerA_isInvisibleToPartnerB_evenWithViewPartnerIdOrFilterPartnerId() {
        Product a = seed(A, "A panel", 5);
        loginAsPartner(B, 21L);
        viewing(A);
        ProductFilterRequestDto filter = new ProductFilterRequestDto();
        filter.setPartnerId(A);

        assertTrue(service.list(filter).getContent().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
        assertTrue(service.catalog().isEmpty());
    }

    @Test
    void productOfPartnerA_isInvisibleInSuperAdminsOwnPlatformList() {
        Product a = seed(A, "A panel", 5);
        Product platform = seed(PLATFORM, "Platform panel", 3);
        loginAsSuperAdmin();                                           // no viewPartnerId

        assertEquals(List.of(platform.getId()), ids(service.list(new ProductFilterRequestDto()).getContent()));
        assertThrows(ResourceNotFoundException.class, () -> service.get(a.getId()));
        List<ProductCatalogItemResponseDto> catalog = service.catalog();
        assertEquals(1, catalog.size());
        assertEquals(platform.getId(), catalog.get(0).getId());
        assertEquals(1L, service.summary().getTotal());
    }

    @Test
    void filterPartnerId_isIgnored_superAdminStillSeesOnlyPlatform() {
        seed(A, "A panel", 5);
        Product platform = seed(PLATFORM, "Platform panel", 3);
        loginAsSuperAdmin();
        ProductFilterRequestDto filter = new ProductFilterRequestDto();
        filter.setPartnerId(A);

        assertEquals(List.of(platform.getId()), ids(service.list(filter).getContent()));
    }

    @Test
    void superAdminSeesPartnerAProduct_onlyWithViewPartnerId() {
        Product a = seed(A, "A panel", 5);
        seed(B, "B panel", 7);
        Product platform = seed(PLATFORM, "Platform panel", 3);
        loginAsSuperAdmin();
        viewing(A);

        assertEquals(List.of(a.getId()), ids(service.list(new ProductFilterRequestDto()).getContent()));
        assertEquals(a.getId(), service.get(a.getId()).getId());
        assertEquals(1, service.catalog().size());
        assertEquals(a.getId(), service.catalog().get(0).getId());
        ProductSummaryResponseDto summary = service.summary();
        assertEquals(1L, summary.getTotal());

        // and B's / the platform's product are not reachable through A's view
        assertThrows(ResourceNotFoundException.class, () -> service.get(platform.getId()));
    }

    @Test
    void partnerUserCannotUseViewPartnerId() {
        Product a = seed(A, "A panel", 5);
        Product b = seed(B, "B panel", 7);
        loginAsPartner(A, 20L);
        viewing(B);

        assertEquals(List.of(a.getId()), ids(service.list(new ProductFilterRequestDto()).getContent()));
        assertThrows(ResourceNotFoundException.class, () -> service.get(b.getId()));
    }

    // ------------------------------------------------------------------ view mode is read only

    @Test
    void superAdminViewingPartner_cannotUpdateStatusAdjustStockMoveStockOrDelete() {
        Product a = seed(A, "A panel", 5);
        loginAsSuperAdmin();
        viewing(A);                                                    // as on a GET
        writeRequestStillCarryingView(A);                              // as on any write: the view is never honoured

        ProductStatusRequestDto status = new ProductStatusRequestDto();
        status.setStatus("INACTIVE");
        StockMovementRequestDto movement = new StockMovementRequestDto();
        movement.setType("IN");
        movement.setQuantity(4);

        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request("Hacked")));
        assertThrows(ResourceNotFoundException.class, () -> service.updateStatus(a.getId(), status));
        assertThrows(ResourceNotFoundException.class, () -> service.adjustStock(a.getId(), adjust(5, "x")));
        assertThrows(ResourceNotFoundException.class, () -> service.addStockMovement(a.getId(), movement));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));

        assertNoWriteHappened();
        assertEquals("A panel", a.getName());
        assertEquals(5, a.getStock());
        assertEquals(RecordStatus.ACTIVE, a.getStatus());
        assertTrue(!a.isDeleted());
    }

    @Test
    void partnerB_cannotWriteToPartnerAProduct() {
        Product a = seed(A, "A panel", 5);
        loginAsPartner(B, 21L);

        assertThrows(ResourceNotFoundException.class, () -> service.update(a.getId(), request("Hacked")));
        assertThrows(ResourceNotFoundException.class, () -> service.adjustStock(a.getId(), adjust(5, "x")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(a.getId()));

        assertNoWriteHappened();
    }

    @Test
    void stockHistory_staysInOwnTenant_evenForSuperAdminViewingAPartner() {
        Product a = seed(A, "A panel", 5);
        loginAsSuperAdmin();
        viewing(A);

        assertThrows(ResourceNotFoundException.class, () -> service.stockHistory(a.getId()));
    }

    // ------------------------------------------------------------------ create: no partner selection

    @Test
    void create_isAlwaysOwnedByTheCallersTenant_superAdminCreatesInPlatformCatalog() {
        loginAsSuperAdmin();
        viewing(A);                                                    // viewing a partner must not redirect a create
        writeRequestStillCarryingView(A);

        ProductResponseDto created = service.create(request("540W Panel"));

        assertNotNull(created);
        assertEquals(1, store.size());
        assertEquals(PLATFORM, store.get(0).getChannelPartnerId());
        verify(partnerGuard).assertUsable(PLATFORM);
    }

    @Test
    void create_partnerUserOwnsItUnderOwnPartner() {
        loginAsPartner(A, 20L);

        service.create(request("540W Panel"));

        assertEquals(A, store.get(0).getChannelPartnerId());
        verify(partnerGuard).assertUsable(A);
    }

    // ------------------------------------------------------------------ audit with previous / new data

    @Test
    void create_isAuditedWithNewValues() {
        loginAsPartner(A, 20L);

        service.create(request("540W Panel"));

        List<Map<String, String>> changes = auditedChanges(AuditAction.CREATE);
        Map<String, String> name = field(changes, "name");
        assertNull(name.get("old"));
        assertEquals("540W Panel", name.get("new"));
        assertEquals("10", field(changes, "stock").get("new"));
    }

    @Test
    void update_isAuditedWithPreviousAndNewValues() {
        Product a = seed(A, "A panel", 5);
        loginAsPartner(A, 20L);
        ProductRequestDto req = request("A panel v2");
        req.setPrice(new BigDecimal("13000"));
        req.setStock(5);

        service.update(a.getId(), req);

        List<Map<String, String>> changes = auditedChanges(AuditAction.UPDATE);
        assertEquals("A panel", field(changes, "name").get("old"));
        assertEquals("A panel v2", field(changes, "name").get("new"));
        assertEquals("12500", field(changes, "price").get("old"));
        assertEquals("13000", field(changes, "price").get("new"));
    }

    @Test
    void statusChange_isAuditedWithPreviousAndNewStatus() {
        Product a = seed(A, "A panel", 5);
        loginAsPartner(A, 20L);
        ProductStatusRequestDto status = new ProductStatusRequestDto();
        status.setStatus("INACTIVE");

        service.updateStatus(a.getId(), status);

        List<Map<String, String>> changes = auditedChanges(AuditAction.STATUS_CHANGE);
        assertEquals("ACTIVE", field(changes, "status").get("old"));
        assertEquals("INACTIVE", field(changes, "status").get("new"));
    }

    @Test
    void delete_isAuditedWithThePreviousData() {
        Product a = seed(A, "A panel", 5);
        loginAsPartner(A, 20L);

        service.delete(a.getId());

        assertTrue(a.isDeleted());
        List<Map<String, String>> changes = auditedChanges(AuditAction.DELETE);
        assertEquals("A panel", field(changes, "name").get("old"));
        assertNull(field(changes, "name").get("new"));
        assertEquals("5", field(changes, "stock").get("old"));
    }

    @Test
    void stockAdjust_changesStock_writesLedgerInOwnTenant_andIsAuditedWithOldAndNewStock() {
        Product a = seed(A, "A panel", 5);
        loginAsPartner(A, 20L);

        service.adjustStock(a.getId(), adjust(-2, "damaged in transit"));

        assertEquals(3, a.getStock());
        assertEquals(1, movements.size());
        assertEquals(A, movements.get(0).getChannelPartnerId());
        assertEquals("OUT", movements.get(0).getType());
        assertEquals(2, movements.get(0).getQuantity());
        assertEquals(3, movements.get(0).getBalanceAfter());

        List<Map<String, String>> changes = auditedChanges(AuditAction.UPDATE);
        assertEquals("5", field(changes, "stock").get("old"));
        assertEquals("3", field(changes, "stock").get("new"));
        assertEquals("damaged in transit", field(changes, "stockReason").get("new"));
        assertEquals("-2", field(changes, "stockAdjustment").get("new"));
    }

    @Test
    void stockMovement_goesThroughTheSameTenantCheckAndAudit() {
        Product platform = seed(PLATFORM, "Platform panel", 1);
        loginAsSuperAdmin();
        StockMovementRequestDto in = new StockMovementRequestDto();
        in.setType("IN");
        in.setQuantity(9);

        service.addStockMovement(platform.getId(), in);

        assertEquals(10, platform.getStock());
        assertEquals(PLATFORM, movements.get(0).getChannelPartnerId());
        assertEquals("10", field(auditedChanges(AuditAction.UPDATE), "stock").get("new"));
    }

    @Test
    void stockCannotGoBelowZero_andNothingIsWrittenOrAudited() {
        Product a = seed(A, "A panel", 1);
        loginAsPartner(A, 20L);

        assertThrows(BusinessException.class, () -> service.adjustStock(a.getId(), adjust(-5, "oops")));

        assertEquals(1, a.getStock());
        assertNoWriteHappened();
    }
}