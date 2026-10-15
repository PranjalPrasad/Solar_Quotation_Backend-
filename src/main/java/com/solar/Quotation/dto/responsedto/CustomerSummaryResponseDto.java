package com.solar.Quotation.dto.responsedto;

public class CustomerSummaryResponseDto {

    private long total;
    private long active;
    private long inactive;
    private long residential;
    private long commercial;
    private long industrial;

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getActive() { return active; }
    public void setActive(long active) { this.active = active; }
    public long getInactive() { return inactive; }
    public void setInactive(long inactive) { this.inactive = inactive; }
    public long getResidential() { return residential; }
    public void setResidential(long residential) { this.residential = residential; }
    public long getCommercial() { return commercial; }
    public void setCommercial(long commercial) { this.commercial = commercial; }
    public long getIndustrial() { return industrial; }
    public void setIndustrial(long industrial) { this.industrial = industrial; }
}
