package com.obsidian.email;

import com.obsidian.model.AuthCheck;
import com.obsidian.model.EmailAttachment;
import com.obsidian.model.EmailLink;
import com.obsidian.model.ScannedEmail;
import com.obsidian.model.Verdict;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** ScannedEmail in web-dashboard/src/lib/types.ts. */
public record ScannedEmailDto(UUID id, String from, String subject, Instant receivedAt, AuthCheck spf, AuthCheck dkim,
        AuthCheck dmarc, boolean verifiedOfficial, Verdict verdict, List<EmailLink> links,
        List<EmailAttachment> attachments) {

    public static ScannedEmailDto of(ScannedEmail email) {
        return new ScannedEmailDto(email.getId(), email.getFromHeader(), email.getSubject(), email.getReceivedAt(),
                email.getSpf(), email.getDkim(), email.getDmarc(), email.isVerifiedOfficial(), email.getVerdict(),
                email.getLinks(), email.getAttachments());
    }
}
