package com.solar.Quotation.dto.requestdto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class ProjectStatusRequestDto {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "PLANNING|IN_PROGRESS|COMPLETED|ON_HOLD", message = "Unknown project status")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
