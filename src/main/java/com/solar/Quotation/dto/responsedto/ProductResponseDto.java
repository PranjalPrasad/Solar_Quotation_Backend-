package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class ProductResponseDto {

    private Long id;
    private String code;
    private String name;
    private String category;
    private String brand;
    private String sku;
    private String spec;
    private String unit;
    private String hsn;
    private BigDecimal gstPercent;
    private Integer warrantyYears;
    private BigDecimal mrp;
    private BigDecimal discountPercent;
    private BigDecimal price;
    private int stock;
    private int lowStockThreshold;
    private BigDecimal qtyPerKw;
    private String status;
    /** IN_STOCK, LOW_STOCK or OUT_OF_STOCK, derived from stock and lowStockThreshold. */
    private String stockStatus;
    private String description;
    private PartnerRefResponseDto partner;
    private AuditInfoResponseDto audit;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStockStatus() { return stockStatus; }
    public void setStockStatus(String stockStatus) { this.stockStatus = stockStatus; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
    public AuditInfoResponseDto getAudit() { return audit; }
    public void setAudit(AuditInfoResponseDto audit) { this.audit = audit; }
}
