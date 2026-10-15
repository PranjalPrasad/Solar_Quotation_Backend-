package com.solar.Quotation.dto.requestdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class FollowupRequestDto {

    @NotNull(message = "Follow-up date is required")
    private LocalDate followupDate;

    @NotBlank(message = "Remark is required")
    @Size(max = 500, message = "Remark is too long")
    private String remark;

    private LocalDate nextDate;

    @Pattern(regexp = "PENDING|COMPLETED", message = "Status must be PENDING or COMPLETED")
    private String status;

    public LocalDate getFollowupDate() { return followupDate; }
    public void setFollowupDate(LocalDate followupDate) { this.followupDate = followupDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDate getNextDate() { return nextDate; }
    public void setNextDate(LocalDate nextDate) { this.nextDate = nextDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}