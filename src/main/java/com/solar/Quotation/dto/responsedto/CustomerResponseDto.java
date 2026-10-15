package com.solar.Quotation.dto.responsedto;

public class CustomerResponseDto {

    private Long id;
    private String code;
    private String name;
    private String mobile;
    private String email;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String gstNo;
    private String type;
    private String consumerNo;
    private String consumerCategory;
    private String status;
    private String projectSummary;
    private long projectCount;
    private long quotationCount;
    private PartnerRefResponseDto partner;
    private AuditInfoResponseDto audit;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getGstNo() { return gstNo; }
    public void setGstNo(String gstNo) { this.gstNo = gstNo; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getConsumerNo() { return consumerNo; }
    public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
    public String getConsumerCategory() { return consumerCategory; }
    public void setConsumerCategory(String consumerCategory) { this.consumerCategory = consumerCategory; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProjectSummary() { return projectSummary; }
    public void setProjectSummary(String projectSummary) { this.projectSummary = projectSummary; }
    public long getProjectCount() { return projectCount; }
    public void setProjectCount(long projectCount) { this.projectCount = projectCount; }
    public long getQuotationCount() { return quotationCount; }
    public void setQuotationCount(long quotationCount) { this.quotationCount = quotationCount; }
    public PartnerRefResponseDto getPartner() { return partner; }
    public void setPartner(PartnerRefResponseDto partner) { this.partner = partner; }
    public AuditInfoResponseDto getAudit() { return audit; }
    public void setAudit(AuditInfoResponseDto audit) { this.audit = audit; }
}
