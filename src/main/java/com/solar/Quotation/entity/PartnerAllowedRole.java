package com.solar.Quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Which roles a channel partner (or the PLATFORM partner) may hand out to its users.
 * A role that is not listed here can never be assigned inside that partner.
 */
@Entity
@Table(name = "partner_allowed_role",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channel_partner_id", "role_id"}))
public class PartnerAllowedRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_partner_id", nullable = false)
    private Long channelPartnerId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    public PartnerAllowedRole() {
    }

    public PartnerAllowedRole(Long channelPartnerId, Long roleId) {
        this.channelPartnerId = channelPartnerId;
        this.roleId = roleId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChannelPartnerId() { return channelPartnerId; }
    public void setChannelPartnerId(Long channelPartnerId) { this.channelPartnerId = channelPartnerId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}