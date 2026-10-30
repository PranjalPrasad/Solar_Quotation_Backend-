package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

public class ProjectResponseDto {

    private Long id;
    private String code;
    private String name;
    private CustomerRefResponseDto customer;
    private String type;
    private BigDecimal capacityKw;
    private String roofType;
    private BigDecimal monthlyConsumption;
    private String location;
    private BigDecimal roofArea;
    private BigDecimal estimatedGeneration;
    private String installationType;
    private String status;
    private String remarks;
    private PartnerRefResponseDto partner;
    private AuditInfoResponseDto audit;
    private Long quotationId;
    private String quotationCode;
    private String quotationStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CustomerRefResponseDto getCustomer() { return customer; }
    public void setCustomer(CustomerRefResponseDto customer) { this.customer = customer; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getCapacityKw() { return capacityKw; }
    public void setCapacityKw(BigDecimal capacityKw) { this.capacityKw = capacityKw; }
    public String getRoofType() { return roofType; }
    public void setRoofType(String roofType) { this.roofType = roofType; }
    public BigDecimal getMonthlyConsumption() { return monthlyConsumption; }
    public void setMonthlyConsumption(BigDecimal monthlyConsumption) { this.monthlyConsumption = monthlyConsumption; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public BigDecimal getRoofArea() { return roofArea; }
    public void setRoofArea(BigDecimal roofArea) { this.roofArea = roofArea; }
    public BigDecimal getEstimatedGeneration() { return estimatedGeneration; }
    public void setEstimatedGeneration(BigDecimal estimatedGeneration) { this.estimatedGeneration = estimatedGeneration; }
    public String getInstallationType() { return installationType; }
    public void setInstallationType(String installationType) { this.installationType = installationType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
    public AuditInfoResponseDto getAudit() { return audit; }
    public void setAudit(AuditInfoResponseDto audit) { this.audit = audit; }
    public Long getQuotationId() { return quotationId; }
    public void setQuotationId(Long quotationId) { this.quotationId = quotationId; }
    public String getQuotationCode() { return quotationCode; }
    public void setQuotationCode(String quotationCode) { this.quotationCode = quotationCode; }
    public String getQuotationStatus() { return quotationStatus; }
    public void setQuotationStatus(String quotationStatus) { this.quotationStatus = quotationStatus; }
}