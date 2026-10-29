package com.solar.Quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "platform_setting", uniqueConstraints = @UniqueConstraint(columnNames = {"channel_partner_id", "setting_key"}))
public class PlatformSetting extends TenantBaseEntity {

    @Column(name = "setting_key", nullable = false, length = 60)
    private String settingKey;

    @Column(name = "value_json", nullable = false, columnDefinition = "TEXT")
    private String valueJson;

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }
    public String getValueJson() { return valueJson; }
    public void setValueJson(String valueJson) { this.valueJson = valueJson; }
}