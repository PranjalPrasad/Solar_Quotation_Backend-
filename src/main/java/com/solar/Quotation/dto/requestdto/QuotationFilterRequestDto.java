package com.solar.Quotation.dto.requestdto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Query string of GET /api/quotations. page, size, sort, search and partnerId come from PageFilterRequestDto. */
public class QuotationFilterRequestDto extends PageFilterRequestDto {

    private String status;
    private Long customerId;
    private Boolean referred;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Boolean getReferred() { return referred; }
    public void setReferred(Boolean referred) { this.referred = referred; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
}
