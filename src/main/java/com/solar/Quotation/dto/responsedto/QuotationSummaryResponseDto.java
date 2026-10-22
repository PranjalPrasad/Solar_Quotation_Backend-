package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

/** The four stat cards above the quotation table. */
public class QuotationSummaryResponseDto {

    private long total;
    private long pending;
    private long accepted;
    private long rejected;
    private BigDecimal totalValue;
    private BigDecimal acceptedValue;

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getPending() { return pending; }
    public void setPending(long pending) { this.pending = pending; }
    public long getAccepted() { return accepted; }
    public void setAccepted(long accepted) { this.accepted = accepted; }
    public long getRejected() { return rejected; }
    public void setRejected(long rejected) { this.rejected = rejected; }
    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }
    public BigDecimal getAcceptedValue() { return acceptedValue; }
    public void setAcceptedValue(BigDecimal acceptedValue) { this.acceptedValue = acceptedValue; }
}
