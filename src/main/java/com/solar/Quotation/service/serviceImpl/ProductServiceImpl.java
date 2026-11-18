package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.Enum.AuditAction;
import com.solar.Quotation.Enum.ProductCategory;
import com.solar.Quotation.Enum.RecordStatus;
import com.solar.Quotation.dto.requestdto.ProductFilterRequestDto;
import com.solar.Quotation.dto.requestdto.ProductRequestDto;
import com.solar.Quotation.dto.requestdto.ProductStatusRequestDto;
import com.solar.Quotation.dto.requestdto.StockAdjustRequestDto;
import com.solar.Quotation.dto.requestdto.StockMovementRequestDto;
import com.solar.Quotation.dto.responsedto.StockMovementResponseDto;
import com.solar.Quotation.entity.ProductStockMovement;
import com.solar.Quotation.repository.ProductStockMovementRepository;
import com.solar.Quotation.dto.responsedto.PageResponse;
import com.solar.Quotation.dto.responsedto.PartnerRefResponseDto;
import com.solar.Quotation.dto.responsedto.ProductCatalogItemResponseDto;
import com.solar.Quotation.dto.responsedto.ProductResponseDto;
import com.solar.Quotation.dto.responsedto.ProductSummaryResponseDto;
import com.solar.Quotation.entity.Product;
import com.solar.Quotation.exception.ApiException;
import com.solar.Quotation.exception.BusinessException;
import com.solar.Quotation.exception.ForbiddenException;
import com.solar.Quotation.exception.ResourceNotFoundException;
import com.solar.Quotation.repository.ProductRepository;
import com.solar.Quotation.security.TenantResolver;
import com.solar.Quotation.service.AuditService;
import com.solar.Quotation.service.PartnerRefProvider;
import com.solar.Quotation.service.ProductService;
import com.solar.Quotation.util.AuditInfoMapper;
import com.solar.Quotation.util.CodeGenerator;
import com.solar.Quotation.util.DiffUtil;
import com.solar.Quotation.util.PartnerGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Products are private to each partner. There is deliberately no owner (data scope OWN) filter here:
 * the catalog is shared by every user of the same partner.
 *
 * Tenant rules (Super Admin works in the PLATFORM catalog like any other tenant):
 *   READS  list / catalog / summary / get  -> tenantResolver.effectiveReadPartnerId(): own tenant, or the partner
 *          named by ?viewPartnerId= for data scope ALL on a GET. A partnerId in the filter is ignored.
 *   WRITES create / update / status / delete / stock -> always the caller's own tenant; a record of another tenant
 *          is refused (404, WARN) through tenantResolver.assertWritable.
 *   STOCK HISTORY and stock changes stay in the caller's own tenant.
 *
 * Logging: [PRODUCT] INFO for create/update/status/delete/stock, WARN for duplicate SKU and validation,
 * ERROR with stack for anything unexpected in a write.
 */
@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "name", "category", "brand", "price", "stock", "status");
    private static final int CATALOG_LIMIT = 1000;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductRepository productRepository;
    private final TenantResolver tenantResolver;
    private final PartnerRefProvider partnerRefProvider;
    private final AuditInfoMapper auditInfoMapper;
    private final AuditService auditService;
    private final CodeGenerator codeGenerator;
    private final PartnerGuard partnerGuard;
    private final ProductStockMovementRepository movementRepository;

    public ProductServiceImpl(ProductRepository productRepository, TenantResolver tenantResolver,
                              PartnerRefProvider partnerRefProvider, AuditInfoMapper auditInfoMapper,
                              AuditService auditService, CodeGenerator codeGenerator, PartnerGuard partnerGuard,
                              ProductStockMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.tenantResolver = tenantResolver;
        this.partnerRefProvider = partnerRefProvider;
        this.auditInfoMapper = auditInfoMapper;
        this.auditService = auditService;
        this.codeGenerator = codeGenerator;
        this.partnerGuard = partnerGuard;
        this.movementRepository = movementRepository;
    }

    // ---------------------------------------------------------------- read

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponseDto> list(ProductFilterRequestDto f) {
        Long partnerId = tenantResolver.effectiveReadPartnerId();   // f.getPartnerId() is deliberately ignored
        Page<Product> page = productRepository.search(partnerId, parseCategory(f.getCategory()),
                parseStatus(f.getStatus()), f.getGstPercent(), parseStockMode(f.getStockStatus()),
                f.getSearchOrNull(), f.toPageable("createdAt,desc", SORT_FIELDS));
        Lookup lookup = lookup(page.getContent());
        return PageResponse.of(page.map(p -> toResponse(p, lookup)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCatalogItemResponseDto> catalog() {
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        return productRepository.findCatalog(partnerId, RecordStatus.ACTIVE, PageRequest.of(0, CATALOG_LIMIT))
                .stream().map(this::toCatalogItem).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSummaryResponseDto summary() {
        Long partnerId = tenantResolver.effectiveReadPartnerId();
        ProductSummaryResponseDto s = new ProductSummaryResponseDto();
        s.setTotal(productRepository.countFiltered(partnerId, null, 0));
        s.setActive(productRepository.countFiltered(partnerId, RecordStatus.ACTIVE, 0));
        s.setLowStock(productRepository.countFiltered(partnerId, null, 2));
        s.setOutOfStock(productRepository.countFiltered(partnerId, null, 1));
        BigDecimal value = productRepository.sumInventoryValue(partnerId);
        s.setInventoryValue(value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP));
        return s;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponseDto get(Long id) {
        return toSingleResponse(findForRead(id));
    }

    // ---------------------------------------------------------------- write

    @Override
    @Transactional
    public ProductResponseDto create(ProductRequestDto req) {
        return guarded("create", () -> doCreate(req));
    }

    private ProductResponseDto doCreate(ProductRequestDto req) {
        Long partnerId = tenantResolver.requirePartnerIdForCreate(null);   // always the caller's own tenant
        partnerGuard.assertUsable(partnerId);

        String sku = blankToNull(req.getSku());
        if (sku != null && productRepository.existsByChannelPartnerIdAndSkuAndDeletedFalse(partnerId, sku)) {
            throw duplicateSku(partnerId, sku);
        }

        Product p = new Product();
        p.setChannelPartnerId(partnerId);
        p.setCode(codeGenerator.next("PRD", partnerId));
        p.setSku(sku);
        applyFields(p, req);
        Product saved = productRepository.save(p);

        auditService.record(AuditAction.CREATE, "PRODUCT", "Product", saved.getId(), saved.getCode(),
                partnerId, null, snapshot(saved));
        log.info("[PRODUCT] created id={} code={} partnerId={} category={} stock={}", saved.getId(), saved.getCode(),
                partnerId, saved.getCategory(), saved.getStock());
        return toSingleResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponseDto update(Long id, ProductRequestDto req) {
        return guarded("update", () -> doUpdate(id, req));
    }

    private ProductResponseDto doUpdate(Long id, ProductRequestDto req) {
        Product p = findForWrite(id);
        String sku = blankToNull(req.getSku());
        if (sku != null && !sku.equals(p.getSku())
                && productRepository.existsByChannelPartnerIdAndSkuAndDeletedFalseAndIdNot(
                p.getChannelPartnerId(), sku, p.getId())) {
            throw duplicateSku(p.getChannelPartnerId(), sku);
        }

        Map<String, Object> before = snapshot(p);
        p.setSku(sku);
        applyFields(p, req);
        Product saved = productRepository.save(p);

        auditService.record(AuditAction.UPDATE, "PRODUCT", "Product", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), before, snapshot(saved));
        log.info("[PRODUCT] updated id={} code={} partnerId={}", saved.getId(), saved.getCode(), saved.getChannelPartnerId());
        return toSingleResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponseDto updateStatus(Long id, ProductStatusRequestDto req) {
        return guarded("status change", () -> doUpdateStatus(id, req));
    }

    private ProductResponseDto doUpdateStatus(Long id, ProductStatusRequestDto req) {
        Product p = findForWrite(id);
        Map<String, Object> before = snapshot(p);
        p.setStatus(RecordStatus.valueOf(req.getStatus()));
        Product saved = productRepository.save(p);

        auditService.record(AuditAction.STATUS_CHANGE, "PRODUCT", "Product", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), before, snapshot(saved));
        log.info("[PRODUCT] status changed id={} code={} partnerId={} {}->{}", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), before.get("status"), saved.getStatus());
        return toSingleResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponseDto adjustStock(Long id, StockAdjustRequestDto req) {
        return guarded("stock adjust", () -> doAdjustStock(id, req));
    }

    private ProductResponseDto doAdjustStock(Long id, StockAdjustRequestDto req) {
        if (req.getAdjustment() == 0) {
            throw invalid("VALIDATION_ERROR", "Adjustment cannot be 0", "stock adjustment is 0 productId=" + id);
        }
        Product p = findForWrite(id);
        long newStock = (long) p.getStock() + req.getAdjustment();
        if (newStock < 0) {
            throw invalid("BUSINESS_RULE", "Stock cannot go below zero",
                    "stock would go below zero id=" + id + " stock=" + p.getStock() + " adjustment=" + req.getAdjustment());
        }
        if (newStock > Integer.MAX_VALUE) {
            throw invalid("VALIDATION_ERROR", "Stock is too large", "stock too large id=" + id);
        }
        int oldStock = p.getStock();

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("stock", String.valueOf(p.getStock()));
        p.setStock((int) newStock);
        Product saved = productRepository.save(p);
        recordMovement(saved, req.getAdjustment(), req.getReason().trim());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("stock", String.valueOf(saved.getStock()));
        after.put("stockReason", req.getReason().trim());
        after.put("stockAdjustment", (req.getAdjustment() > 0 ? "+" : "") + req.getAdjustment());

        auditService.record(AuditAction.UPDATE, "PRODUCT", "Product", saved.getId(), saved.getCode(),
                saved.getChannelPartnerId(), before, after);
        log.info("[PRODUCT] stock changed id={} code={} partnerId={} {}->{} adjustment={} reason=\"{}\"", saved.getId(),
                saved.getCode(), saved.getChannelPartnerId(), oldStock, saved.getStock(), req.getAdjustment(),
                clean(req.getReason()));
        return toSingleResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovementResponseDto> stockHistory(Long id) {
        Product p = findOwn(id);
        return movementRepository.findTop200ByProductIdAndDeletedFalseOrderByCreatedAtDescIdDesc(p.getId())
                .stream().map(this::toMovement).toList();
    }

    @Override
    @Transactional
    public List<StockMovementResponseDto> addStockMovement(Long id, StockMovementRequestDto req) {
        return guarded("stock movement", () -> doAddStockMovement(id, req));
    }

    private List<StockMovementResponseDto> doAddStockMovement(Long id, StockMovementRequestDto req) {
        int qty = req.getQuantity();
        boolean out = "OUT".equals(req.getType());
        String reason = blankToNull(req.getReason());
        if (reason == null) {
            reason = out ? "Stock removed" : "Stock added";
        }
        StockAdjustRequestDto adj = new StockAdjustRequestDto();
        adj.setAdjustment(out ? -qty : qty);
        adj.setReason(reason);
        doAdjustStock(id, adj);                     // validates stock >= 0, updates product, writes ledger + audit
        return stockHistory(id);
    }

    private void recordMovement(Product p, int signedQty, String reason) {
        ProductStockMovement m = new ProductStockMovement();
        m.setChannelPartnerId(p.getChannelPartnerId());
        m.setProductId(p.getId());
        m.setType(signedQty < 0 ? "OUT" : "IN");
        m.setQuantity(Math.abs(signedQty));
        m.setReason(reason);
        m.setBalanceAfter(p.getStock());
        movementRepository.save(m);
    }

    private StockMovementResponseDto toMovement(ProductStockMovement m) {
        StockMovementResponseDto d = new StockMovementResponseDto();
        d.setId(m.getId());
        d.setType(m.getType());
        d.setQuantity(m.getQuantity());
        d.setReason(m.getReason());
        d.setBalanceAfter(m.getBalanceAfter());
        d.setCreatedAt(m.getCreatedAt());
        return d;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        guarded("delete", () -> {
            doDelete(id);
            return null;
        });
    }

    private void doDelete(Long id) {
        Product p = findForWrite(id);
        Map<String, Object> before = snapshot(p);
        String code = p.getCode();
        Long partnerId = p.getChannelPartnerId();
        if (p.getSku() != null) {
            p.setSku(p.getSku() + "~del" + p.getId());        // frees the sku for reuse
        }
        p.setStatus(RecordStatus.INACTIVE);
        p.setDeleted(true);
        productRepository.save(p);
        auditService.record(AuditAction.DELETE, "PRODUCT", "Product", id, code, partnerId, before, null);
        log.info("[PRODUCT] deleted id={} code={} partnerId={}", id, code, partnerId);
    }

    // ---------------------------------------------------------------- helpers

    /** GET: the own tenant, or the viewed partner for Super Admin. */
    private Product findForRead(Long id) {
        return productRepository.findOne(id, tenantResolver.effectiveReadPartnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    /** Always the caller's own tenant, whatever view partner the request carries. */
    private Product findOwn(Long id) {
        return productRepository.findOne(id, tenantResolver.ownPartnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    /**
     * Writes: loads the record without a tenant filter, then refuses anything outside the own tenant.
     * A record of another tenant looks exactly like a missing one (404) and TenantResolver logs it at WARN.
     */
    private Product findForWrite(Long id) {
        Product p = productRepository.findOne(id, null)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        tenantResolver.assertWritable(p.getChannelPartnerId(), "Product", id);
        return p;
    }

    private BusinessException duplicateSku(Long partnerId, String sku) {
        log.warn("[PRODUCT] duplicate SKU partnerId={} sku={}", partnerId, clean(sku));
        return new BusinessException("DUPLICATE", "A product with this SKU already exists", HttpStatus.CONFLICT);
    }

    /** Logs a validation problem at WARN and returns the exception to throw. */
    private BusinessException invalid(String code, String message, String logDetail) {
        log.warn("[PRODUCT] validation: {}", logDetail);
        return new BusinessException(code, message);
    }

    /** Runs a write; anything unexpected is logged at ERROR with the stack, then rethrown to the API error handler. */
    private <T> T guarded(String operation, Supplier<T> body) {
        try {
            return body.get();
        } catch (BusinessException | ResourceNotFoundException | ForbiddenException | ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[PRODUCT] {} failed unexpectedly", operation, e);
            throw e;
        }
    }

    /** Strips control characters from user text before it goes into a log line (log forging). */
    private static String clean(String s) {
        if (s == null) return "-";
        String t = s.replaceAll("[\\r\\n\\t]", " ");
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    private void applyFields(Product p, ProductRequestDto req) {
        p.setName(req.getName().trim());
        p.setCategory(ProductCategory.valueOf(req.getCategory()));
        p.setBrand(blankToNull(req.getBrand()));
        p.setSpec(blankToNull(req.getSpec()));
        p.setUnit(req.getUnit().trim());
        p.setHsn(blankToNull(req.getHsn()));
        p.setGstPercent(req.getGstPercent());
        p.setWarrantyYears(req.getWarrantyYears());
        p.setMrp(req.getMrp());
        p.setDiscountPercent(req.getDiscountPercent());
        p.setPrice(computePrice(req));
        p.setStock(req.getStock());
        p.setLowStockThreshold(req.getLowStockThreshold() == null ? 10 : req.getLowStockThreshold());
        p.setQtyPerKw(req.getQtyPerKw());
        RecordStatus status = parseStatus(req.getStatus());
        p.setStatus(status != null ? status : (p.getStatus() != null ? p.getStatus() : RecordStatus.ACTIVE));
        p.setDescription(blankToNull(req.getDescription()));
    }

    /** price = mrp x (1 - discount / 100) when both are given, otherwise the price that was sent. */
    BigDecimal computePrice(ProductRequestDto req) {
        if (req.getMrp() != null && req.getDiscountPercent() != null) {
            BigDecimal factor = BigDecimal.ONE.subtract(req.getDiscountPercent().divide(HUNDRED, 6, RoundingMode.HALF_UP));
            return req.getMrp().multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }
        if (req.getPrice() == null) {
            throw invalid("VALIDATION_ERROR", "Price is required when MRP and discount are not both given",
                    "price missing and mrp/discount not both given");
        }
        return req.getPrice().setScale(2, RoundingMode.HALF_UP);
    }

    static String stockStatus(Product p) {
        if (p.getStock() <= 0) {
            return "OUT_OF_STOCK";
        }
        return p.getStock() <= p.getLowStockThreshold() ? "LOW_STOCK" : "IN_STOCK";
    }

    private ProductCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ProductCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("VALIDATION_ERROR", "Unknown category " + value, "unknown category " + clean(value));
        }
    }

    private RecordStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RecordStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid("VALIDATION_ERROR", "Unknown status " + value, "unknown status " + clean(value));
        }
    }

    /** 0 = any, 1 = out of stock, 2 = low stock, 3 = in stock (same codes as ProductRepository). */
    private int parseStockMode(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "OUT_OF_STOCK" -> 1;
            case "LOW_STOCK" -> 2;
            case "IN_STOCK" -> 3;
            default -> throw invalid("VALIDATION_ERROR", "Unknown stock status " + value,
                    "unknown stock status " + clean(value));
        };
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String plain(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private Map<String, Object> snapshot(Product p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("category", p.getCategory().name());
        m.put("brand", p.getBrand());
        m.put("sku", p.getSku());
        m.put("spec", p.getSpec());
        m.put("unit", p.getUnit());
        m.put("hsn", p.getHsn());
        m.put("gstPercent", plain(p.getGstPercent()));
        m.put("warrantyYears", p.getWarrantyYears() == null ? null : p.getWarrantyYears().toString());
        m.put("mrp", plain(p.getMrp()));
        m.put("discountPercent", plain(p.getDiscountPercent()));
        m.put("price", plain(p.getPrice()));
        m.put("stock", String.valueOf(p.getStock()));
        m.put("lowStockThreshold", String.valueOf(p.getLowStockThreshold()));
        m.put("qtyPerKw", plain(p.getQtyPerKw()));
        m.put("status", p.getStatus().name());
        m.put("description", p.getDescription());
        return m;
    }

    private static final class Lookup {
        private Map<Long, PartnerRefResponseDto> partners = new HashMap<>();
        private Map<Long, String> names = new HashMap<>();
    }

    private Lookup lookup(List<Product> products) {
        Lookup lk = new Lookup();
        if (products.isEmpty()) {
            return lk;
        }
        Set<Long> partnerIds = new HashSet<>();
        for (Product p : products) {
            partnerIds.add(p.getChannelPartnerId());
        }
        lk.partners = partnerRefProvider.refs(partnerIds);
        lk.names = auditInfoMapper.namesFor(products);
        return lk;
    }

    private ProductResponseDto toSingleResponse(Product p) {
        return toResponse(p, lookup(List.of(p)));
    }

    private ProductResponseDto toResponse(Product p, Lookup lk) {
        ProductResponseDto d = new ProductResponseDto();
        d.setId(p.getId());
        d.setCode(p.getCode());
        d.setName(p.getName());
        d.setCategory(p.getCategory().name());
        d.setBrand(p.getBrand());
        d.setSku(p.getSku());
        d.setSpec(p.getSpec());
        d.setUnit(p.getUnit());
        d.setHsn(p.getHsn());
        d.setGstPercent(p.getGstPercent());
        d.setWarrantyYears(p.getWarrantyYears());
        d.setMrp(p.getMrp());
        d.setDiscountPercent(p.getDiscountPercent());
        d.setPrice(p.getPrice());
        d.setStock(p.getStock());
        d.setLowStockThreshold(p.getLowStockThreshold());
        d.setQtyPerKw(p.getQtyPerKw());
        d.setStatus(p.getStatus().name());
        d.setStockStatus(stockStatus(p));
        d.setDescription(p.getDescription());
        d.setPartner(lk.partners.get(p.getChannelPartnerId()));
        d.setAudit(auditInfoMapper.of(p, lk.names));
        return d;
    }

    private ProductCatalogItemResponseDto toCatalogItem(Product p) {
        ProductCatalogItemResponseDto d = new ProductCatalogItemResponseDto();
        d.setId(p.getId());
        d.setName(p.getName());
        d.setCategory(p.getCategory().name());
        d.setBrand(p.getBrand());
        d.setSpec(p.getSpec());
        d.setUnit(p.getUnit());
        d.setHsn(p.getHsn());
        d.setGstPercent(p.getGstPercent());
        d.setPrice(p.getPrice());
        d.setQtyPerKw(p.getQtyPerKw());
        d.setWarrantyYears(p.getWarrantyYears());
        d.setStock(p.getStock());
        return d;
    }
}