package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create and update. partnerId is read only for SUPER_ADMIN on create (required there).
 * When mrp and discountPercent are both sent, price is recomputed and the sent price is ignored.
 * Otherwise price is required.
 */
public class ProductRequestDto {

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Name is too long")
    private String name;

    @NotBlank(message = "Category is required")
    @Pattern(regexp = "SOLAR_PANEL|INVERTER|BATTERY|MOUNTING_STRUCTURE|CABLE_WIRING|ACCESSORY",
            message = "Unknown category")
    private String category;

    @Size(max = 100, message = "Brand is too long")
    private String brand;

    @Size(max = 60, message = "SKU is too long")
    private String sku;

    @Size(max = 200, message = "Specification is too long")
    private String spec;

    @NotBlank(message = "Unit is required")
    @Size(max = 20, message = "Unit is too long")
    private String unit;

    @Size(max = 20, message = "HSN is too long")
    private String hsn;

    @NotNull(message = "GST percent is required")
    @DecimalMin(value = "0", message = "GST cannot be negative")
    @DecimalMax(value = "100", message = "GST cannot exceed 100")
    private BigDecimal gstPercent;

    @Min(value = 0, message = "Warranty cannot be negative")
    @Max(value = 100, message = "Warranty is too long")
    private Integer warrantyYears;

    @DecimalMin(value = "0", message = "MRP cannot be negative")
    private BigDecimal mrp;

    @DecimalMin(value = "0", message = "Discount cannot be negative")
    @DecimalMax(value = "100", message = "Discount cannot exceed 100")
    private BigDecimal discountPercent;

    @DecimalMin(value = "0", message = "Price cannot be negative")
    private BigDecimal price;

    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock cannot be negative")
    private Integer stock;

    @Min(value = 0, message = "Low stock threshold cannot be negative")
    private Integer lowStockThreshold;

    @DecimalMin(value = "0", message = "Quantity per kW cannot be negative")
    private BigDecimal qtyPerKw;

    @Pattern(regexp = "ACTIVE|INACTIVE", message = "Status must be ACTIVE or INACTIVE")
    private String status;

    @Size(max = 500, message = "Description is too long")
    private String description;

    private Long partnerId;

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
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(Integer lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public BigDecimal getQtyPerKw() { return qtyPerKw; }
    public void setQtyPerKw(BigDecimal qtyPerKw) { this.qtyPerKw = qtyPerKw; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
}
