package com.solar.Quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A sidebar screen area, e.g. QUOTATION. Platform-only modules can never be granted to partner roles. */
@Entity
@Table(name = "module")
public class AppModule extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 150)
    private String route;

    @Column(name = "platform_only", nullable = false)
    private boolean platformOnly;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public boolean isPlatformOnly() { return platformOnly; }
    public void setPlatformOnly(boolean platformOnly) { this.platformOnly = platformOnly; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
