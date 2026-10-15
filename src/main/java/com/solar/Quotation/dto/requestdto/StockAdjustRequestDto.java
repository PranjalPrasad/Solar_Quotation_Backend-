package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** adjustment is a signed change, for example -10 or 25. It must not be 0. */
public class StockAdjustRequestDto {

    @NotNull(message = "Adjustment is required")
    private Integer adjustment;

    @NotBlank(message = "Reason is required")
    @Size(max = 200, message = "Reason is too long")
    private String reason;

    public Integer getAdjustment() { return adjustment; }
    public void setAdjustment(Integer adjustment) { this.adjustment = adjustment; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
