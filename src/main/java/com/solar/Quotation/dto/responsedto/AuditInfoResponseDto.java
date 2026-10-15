package com.solar.Quotation.dto.responsedto;
import java.time.LocalDateTime;

public class AuditInfoResponseDto {

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    public AuditInfoResponseDto() { }
    public AuditInfoResponseDto(LocalDateTime createdAt, String createdBy, LocalDateTime updatedAt, String updatedBy) {
        this.createdAt = createdAt; this.createdBy = createdBy; this.updatedAt = updatedAt; this.updatedBy = updatedBy;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
