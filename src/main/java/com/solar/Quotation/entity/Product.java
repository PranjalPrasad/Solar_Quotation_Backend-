package com.solar.Quotation.entity;
import com.solar.Quotation.Enum.ProductCategory;
import com.solar.Quotation.Enum.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/** Products are private to each channel partner (strict isolation). sku is unique inside a partner. */
@Entity
@Table(name = "product",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_partner_id", "sku"}),
        indexes = {
                @Index(name = "idx_product_partner_created", columnList = "channel_partner_id,created_at"),
                @Index(name = "idx_product_partner_category", columnList = "channel_partner_id,category")
        })
public class Product extends TenantBaseEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductCategory category;

    @Column(length = 100)
    private String brand;

    /** Optional. Longer column because a deleted product's sku gets a suffix so it can be reused. */
    @Column(length = 100)
    private String sku;

    @Column(length = 200)
    private String spec;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(length = 20)
    private String hsn;

    @Column(name = "gst_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercent = BigDecimal.ZERO;

    @Column(name = "warranty_years")
    private Integer warrantyYears;

    @Column(precision = 14, scale = 2)
    private BigDecimal mrp;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** Selling price. Recomputed from mrp and discountPercent whenever both are present. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 10;

    /** Quantity needed per kW of system size (used by the quotation to pre-fill quantities). */
    @Column(name = "qty_per_kw", precision = 8, scale = 3)
    private BigDecimal qtyPerKw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(length = 500)
    private String description;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public Integer getWarrantyYears() { return warrantyYears; }
    public void setWarrantyYears(Integer warrantyYears) { this.warrantyYears = warrantyYears; }
    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public BigDecimal getQtyPerKw() { return qtyPerKw; }
    public void setQtyPerKw(BigDecimal qtyPerKw) { this.qtyPerKw = qtyPerKw; }
    public RecordStatus getStatus() { return status; }
    public void setStatus(RecordStatus status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
