package com.solar.Quotation.dto.responsedto;

import java.util.Map;

public class ProjectSummaryResponseDto {

    private long total;
    private long residential;
    private long commercial;
    private long industrial;
    private Map<String, Long> byStatus;

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getResidential() { return residential; }
    public void setResidential(long residential) { this.residential = residential; }
    public long getCommercial() { return commercial; }
    public void setCommercial(long commercial) { this.commercial = commercial; }
    public long getIndustrial() { return industrial; }
    public void setIndustrial(long industrial) { this.industrial = industrial; }
    public Map<String, Long> getByStatus() { return byStatus; }
    public void setByStatus(Map<String, Long> byStatus) { this.byStatus = byStatus; }
}
