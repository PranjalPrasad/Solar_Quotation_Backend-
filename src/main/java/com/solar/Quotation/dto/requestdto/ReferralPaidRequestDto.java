package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** PATCH /api/referrals/{id}/mark-paid (Super Admin only). */
public class ReferralPaidRequestDto {

    @NotNull(message = "Paid date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate paidOn;

    @NotBlank(message = "Payment mode is required")
    @Pattern(regexp = "BANK_TRANSFER|UPI|CHEQUE|CASH", message = "Payment mode must be BANK_TRANSFER, UPI, CHEQUE or CASH")
    private String paymentMode;

    /** Required for every mode except CASH (checked in the service). */
    @Size(max = 100, message = "Payment reference is too long")
    private String paymentReference;

    @Size(max = 300, message = "Remarks are too long")
    private String remarks;

    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
