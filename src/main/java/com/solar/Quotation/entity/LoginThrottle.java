package com.solar.Quotation.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Failed-attempt counter per (scope, subject). Scope is USER (typed login), IP or RESET_IP.
 * The subject is stored only as a hash: people sometimes type a password into the username box.
 */
@Entity
@Table(name = "login_throttle",
        uniqueConstraints = @UniqueConstraint(columnNames = {"throttle_scope", "subject_hash"}))
public class LoginThrottle extends BaseEntity {

    @Column(name = "throttle_scope", nullable = false, length = 12)
    private String scope;

    @Column(name = "subject_hash", nullable = false, length = 64)
    private String subjectHash;

    @Column(nullable = false)
    private int failures;

    @Column(name = "window_start")
    private LocalDateTime windowStart;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getSubjectHash() { return subjectHash; }
    public void setSubjectHash(String subjectHash) { this.subjectHash = subjectHash; }
    public int getFailures() { return failures; }
    public void setFailures(int failures) { this.failures = failures; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
}
