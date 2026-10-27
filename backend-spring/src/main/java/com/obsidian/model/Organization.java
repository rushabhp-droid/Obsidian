package com.obsidian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "policy_allow_user_bypass")
    private boolean policyAllowUserBypass = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected Organization() {}

    public Organization(String name) {
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public boolean isPolicyAllowUserBypass() { return policyAllowUserBypass; }
    public void setPolicyAllowUserBypass(boolean allow) { this.policyAllowUserBypass = allow; }
    public Instant getCreatedAt() { return createdAt; }
}
