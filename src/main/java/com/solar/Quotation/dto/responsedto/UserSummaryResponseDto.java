package com.solar.Quotation.dto.responsedto;

/** KPI cards of the Users screen. Counts are scoped to the caller's partner (Super Admin: everything). */
public class UserSummaryResponseDto {

    private long total;
    private long active;
    private long inactive;
    private long admins;
    private long superAdmins;
    private long partnerAdmins;
    private long partnerSales;

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getActive() { return active; }
    public void setActive(long active) { this.active = active; }
    public long getInactive() { return inactive; }
    public void setInactive(long inactive) { this.inactive = inactive; }
    public long getAdmins() { return admins; }
    public void setAdmins(long admins) { this.admins = admins; }
    public long getSuperAdmins() { return superAdmins; }
    public void setSuperAdmins(long superAdmins) { this.superAdmins = superAdmins; }
    public long getPartnerAdmins() { return partnerAdmins; }
    public void setPartnerAdmins(long partnerAdmins) { this.partnerAdmins = partnerAdmins; }
    public long getPartnerSales() { return partnerSales; }
    public void setPartnerSales(long partnerSales) { this.partnerSales = partnerSales; }
}
