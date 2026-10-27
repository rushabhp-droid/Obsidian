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
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A Gmail message after scanning. The body is never stored. */
@Entity
@Table(name = "scanned_emails")
public class ScannedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "gmail_message_id", nullable = false, length = 64)
    private String gmailMessageId;

    @Column(name = "from_header", nullable = false)
    private String fromHeader;

    @Column(name = "sender_address")
    private String senderAddress;

    private String subject;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private AuthCheck spf = AuthCheck.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private AuthCheck dkim = AuthCheck.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private AuthCheck dmarc = AuthCheck.NONE;

    @Column(name = "verified_official", nullable = false)
    private boolean verifiedOfficial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verdict verdict = Verdict.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<EmailLink> links = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<EmailAttachment> attachments = List.of();

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected ScannedEmail() {}

    public ScannedEmail(UUID userId, String gmailMessageId) {
        this.userId = userId;
        this.gmailMessageId = gmailMessageId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getGmailMessageId() { return gmailMessageId; }
    public String getFromHeader() { return fromHeader; }
    public void setFromHeader(String fromHeader) { this.fromHeader = fromHeader; }
    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String senderAddress) { this.senderAddress = senderAddress; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    public AuthCheck getSpf() { return spf; }
    public void setSpf(AuthCheck spf) { this.spf = spf; }
    public AuthCheck getDkim() { return dkim; }
    public void setDkim(AuthCheck dkim) { this.dkim = dkim; }
    public AuthCheck getDmarc() { return dmarc; }
    public void setDmarc(AuthCheck dmarc) { this.dmarc = dmarc; }
    public boolean isVerifiedOfficial() { return verifiedOfficial; }
    public void setVerifiedOfficial(boolean verifiedOfficial) { this.verifiedOfficial = verifiedOfficial; }
    public Verdict getVerdict() { return verdict; }
    public void setVerdict(Verdict verdict) { this.verdict = verdict; }
    public List<EmailLink> getLinks() { return links; }
    public void setLinks(List<EmailLink> links) { this.links = links; }
    public List<EmailAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<EmailAttachment> attachments) { this.attachments = attachments; }
    public Instant getCreatedAt() { return createdAt; }
}
