package com.solar.Quotation.dto.responsedto;

/**
 * Tells the screen WHOSE data it is showing, so the label never has to be guessed from the role.
 *   mode OWN          the caller's own tenant ("My workspace"; for Super Admin this is the PLATFORM tenant)
 *   mode PARTNER_VIEW a Super Admin looking at one partner through ?viewPartnerId= (read only)
 */
public class DashboardScopeResponseDto {

    public static final String OWN = "OWN";
    public static final String PARTNER_VIEW = "PARTNER_VIEW";

    private String mode;
    private Long partnerId;
    private String partnerName;
    private String partnerCode;
    private String label;
    /** True when data scope OWN limits the numbers to records created by this user. */
    private boolean ownRecordsOnly;
    /** True in PARTNER_VIEW: the screen must hide create / edit actions (the server refuses them anyway). */
    private boolean readOnly;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Long getPartnerId() { return partnerId; }
    public void setPartnerId(Long partnerId) { this.partnerId = partnerId; }
    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }
    public String getPartnerCode() { return partnerCode; }
    public void setPartnerCode(String partnerCode) { this.partnerCode = partnerCode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isOwnRecordsOnly() { return ownRecordsOnly; }
    public void setOwnRecordsOnly(boolean ownRecordsOnly) { this.ownRecordsOnly = ownRecordsOnly; }
    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
}