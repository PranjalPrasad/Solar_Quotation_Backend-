package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class ProductCatalogItemResponseDto {

    private Long id;
    private String name;
    private String category;
    private String brand;
    private String spec;
    private String unit;
    private String hsn;
    private BigDecimal gstPercent;
    private BigDecimal price;
    private BigDecimal qtyPerKw;
    private Integer warrantyYears;
    private int stock;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }
    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getQtyPerKw() { return qtyPerKw; }
    public void setQtyPerKw(BigDecimal qtyPerKw) { this.qtyPerKw = qtyPerKw; }
    public Integer getWarrantyYears() { return warrantyYears; }
    public void setWarrantyYears(Integer warrantyYears) { this.warrantyYears = warrantyYears; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
