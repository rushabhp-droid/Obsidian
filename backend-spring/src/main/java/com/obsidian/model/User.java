package com.obsidian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * users row. The encrypted columns are written and read only with
 * pgp_sym_encrypt / pgp_sym_decrypt in native SQL (GmailTokenStore), so JPA
 * maps them read-only and never overwrites them. TOTP and SIM columns are [P1].
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.CONSUMER;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "totp_secret_enc", insertable = false, updatable = false)
    private byte[] totpSecretEnc;

    @Column(name = "totp_enabled")
    private boolean totpEnabled;

    @Column(name = "sim_fingerprint_hash", length = 64)
    private String simFingerprintHash;

    @Column(name = "gmail_refresh_token_enc", insertable = false, updatable = false)
    private byte[] gmailRefreshTokenEnc;

    @Column(name = "gmail_address")
    private String gmailAddress;

    @Column(name = "gmail_last_polled_at")
    private Instant gmailLastPolledAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected User() {}

    public User(String email, Role role) {
        this.email = email;
        this.role = role;
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public String getGmailAddress() { return gmailAddress; }
    public void setGmailAddress(String gmailAddress) { this.gmailAddress = gmailAddress; }
    public Instant getGmailLastPolledAt() { return gmailLastPolledAt; }
    public void setGmailLastPolledAt(Instant at) { this.gmailLastPolledAt = at; }
    public Instant getCreatedAt() { return createdAt; }
}
