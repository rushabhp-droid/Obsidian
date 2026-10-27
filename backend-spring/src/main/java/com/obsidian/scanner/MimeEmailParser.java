package com.obsidian.scanner;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a raw RFC 822 message in memory: headers, links from text and HTML
 * parts, and a SHA-256 of every attachment. Bodies and attachment bytes are
 * never stored.
 */
public final class MimeEmailParser {

    public record Attachment(String filename, String sha256) {}

    public record ParsedEmail(String from, String senderAddress, String subject, Instant receivedAt,
            List<String> authenticationResults, List<String> links, List<Attachment> attachments) {}

    private static final Pattern HREF = Pattern.compile("(?i)href\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final int MAX_DEPTH = 10;

    private MimeEmailParser() {}

    public static ParsedEmail parse(byte[] raw) {
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
            Address[] from = message.getFrom();
            String fromHeader = from == null || from.length == 0 ? "" : decode(from[0].toString());
            String address = from != null && from.length > 0 && from[0] instanceof InternetAddress internet
                    ? internet.getAddress().toLowerCase(Locale.ROOT) : null;
            String[] auth = message.getHeader("Authentication-Results");
            Instant sent = message.getSentDate() == null ? Instant.now() : message.getSentDate().toInstant();

            Set<String> links = new LinkedHashSet<>();
            List<Attachment> attachments = new ArrayList<>();
            walk(message, links, attachments, 0);
            return new ParsedEmail(fromHeader, address, message.getSubject(), sent,
                    auth == null ? List.of() : Arrays.asList(auth), List.copyOf(links), attachments);
        } catch (MessagingException | IOException e) {
            throw new IllegalArgumentException("Unreadable email: " + e.getMessage(), e);
        }
    }

    private static void walk(Part part, Set<String> links, List<Attachment> attachments, int depth)
            throws MessagingException, IOException {
        if (depth > MAX_DEPTH) {
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                walk(child, links, attachments, depth + 1);
            }
            return;
        }
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null;
        if (attachment) {
            try (InputStream in = part.getInputStream()) {
                String name = part.getFileName() == null ? "attachment" : decode(part.getFileName());
                attachments.add(new Attachment(name, sha256(in)));
            }
        } else if (part.isMimeType("text/html")) {
            String html = part.getContent().toString();
            Matcher href = HREF.matcher(html);
            while (href.find()) {
                UrlExtractor.clean(href.group(1)).ifPresent(links::add);
            }
            links.addAll(UrlExtractor.extract(html.replaceAll("<[^>]*>", " ")));
        } else if (part.isMimeType("text/*")) {
            links.addAll(UrlExtractor.extract(part.getContent().toString()));
        }
    }

    private static String sha256(InputStream in) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String decode(String value) {
        try {
            return MimeUtility.decodeText(value);
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

}
