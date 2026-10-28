package com.solar.Quotation.dto.requestdto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class ReferralFilterRequestDto extends PageFilterRequestDto {

    private String status;
    private Long referrerPartnerId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReferrerPartnerId() { return referrerPartnerId; }
    public void setReferrerPartnerId(Long referrerPartnerId) { this.referrerPartnerId = referrerPartnerId; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
}
