package com.solar.Quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One action on one module. code = MODULE:ACTION, e.g. QUOTATION:CREATE. */
@Entity
@Table(name = "permission")
public class Permission extends BaseEntity {

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(nullable = false, length = 80)
    private String code;

    public Long getModuleId() { return moduleId; }
    public void setModuleId(Long moduleId) { this.moduleId = moduleId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
