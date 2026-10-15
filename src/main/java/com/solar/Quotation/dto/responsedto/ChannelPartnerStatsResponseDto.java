package com.solar.Quotation.dto.responsedto;

import java.math.BigDecimal;

/**
 * Counts for one partner. users is live now. customers, projects, quotations and the money fields stay 0 until
 * those modules exist; their services then fill them in ChannelPartnerServiceImpl.statsFor().
 */
public class ChannelPartnerStatsResponseDto {

    private long customers;
    private long projects;
    private long quotations;
    private long users;
    private BigDecimal quotationValue = BigDecimal.ZERO;
    private BigDecimal referralEarned = BigDecimal.ZERO;
    private BigDecimal referralPaid = BigDecimal.ZERO;
    private BigDecimal referralPending = BigDecimal.ZERO;

    public long getCustomers() { return customers; }
    public void setCustomers(long customers) { this.customers = customers; }
    public long getProjects() { return projects; }
    public void setProjects(long projects) { this.projects = projects; }
    public long getQuotations() { return quotations; }
    public void setQuotations(long quotations) { this.quotations = quotations; }
    public long getUsers() { return users; }
    public void setUsers(long users) { this.users = users; }
    public BigDecimal getQuotationValue() { return quotationValue; }
    public void setQuotationValue(BigDecimal quotationValue) { this.quotationValue = quotationValue; }
    public BigDecimal getReferralEarned() { return referralEarned; }
    public void setReferralEarned(BigDecimal referralEarned) { this.referralEarned = referralEarned; }
    public BigDecimal getReferralPaid() { return referralPaid; }
    public void setReferralPaid(BigDecimal referralPaid) { this.referralPaid = referralPaid; }
    public BigDecimal getReferralPending() { return referralPending; }
    public void setReferralPending(BigDecimal referralPending) { this.referralPending = referralPending; }
}
