-- Obsidian schema. Hibernate runs with ddl-auto=validate, so the
-- JPA entities in backend-spring/model must match this file exactly.
-- Runs once, when the Postgres volume is empty (docker-entrypoint-initdb.d).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Organizations (NULL org_id = Consumer tier; Populated = Enterprise tier)
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    policy_allow_user_bypass BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Users & Auth Credentials
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    email VARCHAR(255) UNIQUE NOT NULL,           -- stored lowercase
    display_name VARCHAR(120) NULL,               -- shown in the dashboard shell
    role VARCHAR(20) NOT NULL DEFAULT 'CONSUMER', -- 'CONSUMER', 'EMPLOYEE', 'ADMIN'
    password_hash TEXT NULL,                      -- Argon2id hash; NULL for passkey-only accounts
    totp_secret_enc BYTEA NULL,                   -- [P1] pgp_sym_encrypt(base32 secret, app key)
    totp_enabled BOOLEAN DEFAULT FALSE,           -- [P1]
    sim_fingerprint_hash VARCHAR(64) NULL,        -- [P1] SHA-256 of (CarrierId + MCC + MNC + Slot)
    gmail_refresh_token_enc BYTEA NULL,           -- pgp_sym_encrypt(refresh token, app key)
    gmail_address VARCHAR(255) NULL,              -- mailbox the refresh token belongs to
    gmail_last_polled_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. WebAuthn / Passkey Credentials (FIDO2)
-- Copied verbatim from Spring Security 6.5.11 (user-entities-schema.sql and
-- user-credentials-schema-postgres.sql). Spring's JDBC repositories own these
-- tables. user_entities.name holds users.email.
create table user_entities
(
    id           varchar(1000) not null,
    name         varchar(100)  not null,
    display_name varchar(200),
    primary key (id)
);

create table user_credentials
(
    credential_id                varchar(1000) not null,
    user_entity_user_id          varchar(1000) not null,
    public_key                   bytea         not null,
    signature_count              bigint,
    uv_initialized               boolean,
    backup_eligible              boolean       not null,
    authenticator_transports     varchar(1000),
    public_key_credential_type   varchar(100),
    backup_state                 boolean       not null,
    attestation_object           bytea,
    attestation_client_data_json bytea,
    created                      timestamp,
    last_used                    timestamp,
    label                        varchar(1000) not null,
    primary key (credential_id)
);

-- 4. Unified Threat Rules (Blacklist & Whitelist for Email, SMS/WhatsApp, Firewall)
-- One row per (scope, indicator): Report/Bypass flip list_type with
-- INSERT ... ON CONFLICT (...) DO UPDATE. Only RuleService writes this table.
CREATE TABLE threat_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    indicator_type VARCHAR(20) NOT NULL, -- 'EMAIL', 'PHONE', 'URL', 'DOMAIN', 'IP', 'FILE_HASH'
    indicator_value TEXT NOT NULL,       -- Normalized lowercase value
    list_type VARCHAR(12) NOT NULL,      -- 'BLACKLIST' or 'WHITELIST'
    threat_category VARCHAR(30) NOT NULL DEFAULT 'MALICIOUS', -- 'SAFE', 'SPAM', 'PHISHING', 'MALWARE', 'MALICIOUS'
    source VARCHAR(30) NOT NULL,         -- 'VIRUSTOTAL', 'USER_REPORT', 'USER_BYPASS', 'ADMIN_POLICY'
    confidence_score INT DEFAULT 100,
    expires_at TIMESTAMPTZ NULL,         -- Temporary bypasses (single source of truth for TTL)
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE NULLS NOT DISTINCT (org_id, user_id, indicator_type, indicator_value)
);

CREATE INDEX idx_threat_fast_lookup ON threat_rules (indicator_type, indicator_value, list_type);
CREATE INDEX idx_threat_org_sync ON threat_rules (org_id, updated_at DESC);
CREATE INDEX idx_threat_expiry ON threat_rules (expires_at) WHERE expires_at IS NOT NULL;

-- 5. Endpoint Agents (Rust daemons)
CREATE TABLE endpoint_agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    hostname VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 of the agent's bearer token
    last_heartbeat_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Audit & Scan Telemetry Logs
-- One scan_logs row per scan request; indicator_checked is its worst indicator.
CREATE TABLE scan_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    channel VARCHAR(20) NOT NULL,        -- 'EMAIL', 'SMS', 'WHATSAPP', 'FIREWALL', 'CODE'
    sender VARCHAR(255),
    indicator_checked TEXT,
    verdict VARCHAR(20) NOT NULL,        -- 'CLEAN', 'SUSPICIOUS', 'MALICIOUS', 'BYPASSED', 'PENDING'
    vt_positives INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_scan_logs_user ON scan_logs (user_id, created_at DESC);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NULL REFERENCES organizations(id) ON DELETE CASCADE,
    actor_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(30) NOT NULL,         -- 'REPORT', 'BYPASS', 'BYPASS_EXPIRED', 'RULE_EDIT', 'AGENT_REGISTER'
    rule_id UUID NULL REFERENCES threat_rules(id) ON DELETE SET NULL,
    details JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_org ON audit_logs (org_id, created_at DESC);

-- 7. Code Guard findings [P1]
-- Never store a secret's value. Store the detector rule, location and a
-- fingerprint (SHA-256 of the secret) so repeat leaks can be matched.
-- No code uses this table until Code Guard is built.
CREATE TABLE code_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    repo_full_name VARCHAR(255) NOT NULL,  -- 'acme/payments-api'
    trigger VARCHAR(10) NOT NULL,          -- 'PUSH', 'PR', 'RELEASE'
    git_ref VARCHAR(255) NOT NULL,         -- commit SHA or release tag
    finding_type VARCHAR(20) NOT NULL,     -- 'SECRET', 'VULN_DEPENDENCY', 'MALICIOUS_BINARY', 'VULN_BINARY'
    severity VARCHAR(10) NOT NULL,         -- 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'
    rule_id VARCHAR(120) NOT NULL,         -- gitleaks rule, OSV/CVE id, or 'VT:<detections>'
    location TEXT,                         -- path:line, package@version, or asset filename
    secret_fingerprint VARCHAR(64) NULL,   -- SHA-256 of the secret, never the secret itself
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'REVOKED', 'FALSE_POSITIVE'
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_code_findings_org ON code_findings (org_id, created_at DESC);

-- 8. Scanned Gmail messages (dashboard Email security page; dedupes polling).
-- One row per (user, Gmail message). Message bodies are never stored.
CREATE TABLE scanned_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gmail_message_id VARCHAR(64) NOT NULL,
    from_header TEXT NOT NULL,                -- display form: 'Payroll <payroll@x.example>'
    sender_address VARCHAR(255),              -- normalized lowercase address
    subject TEXT,
    received_at TIMESTAMPTZ NOT NULL,
    spf VARCHAR(4) NOT NULL,                  -- 'PASS', 'FAIL', 'NONE'
    dkim VARCHAR(4) NOT NULL,
    dmarc VARCHAR(4) NOT NULL,
    verified_official BOOLEAN NOT NULL DEFAULT FALSE,
    verdict VARCHAR(20) NOT NULL,             -- 'CLEAN', 'SUSPICIOUS', 'MALICIOUS', 'PENDING'
    links JSONB NOT NULL DEFAULT '[]',        -- [{url, verdict, vtDetections}]
    attachments JSONB NOT NULL DEFAULT '[]',  -- [{filename, sha256, verdict, vtDetections}]
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (user_id, gmail_message_id)
);

CREATE INDEX idx_scanned_emails_user ON scanned_emails (user_id, received_at DESC);
