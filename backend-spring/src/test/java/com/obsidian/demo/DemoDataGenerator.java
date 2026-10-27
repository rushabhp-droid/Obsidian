package com.obsidian.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.config.SecurityBeans;
import com.obsidian.model.Role;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Generates the official demo data into a gitignored directory (default ../demo):
 * <ul>
 *   <li>{@code CREDENTIALS.md} and {@code credentials.env}: a fresh random password per account (0600);</li>
 *   <li>{@code seed.sql}: organization, accounts (Argon2id hashes only), a day of scan telemetry and the
 *       Email security inbox, all relative to NOW() and idempotent.</li>
 * </ul>
 * Threat rules aren't in the SQL: only RuleService writes them. {@code rules.json} lists them, and
 * {@code scripts/build.sh run --demo} has the backend apply it through RuleService at startup. Run: {@code gradle -q generateDemoData --args="../demo [--force]"}.
 */
public final class DemoDataGenerator {

    public record Account(String key, String email, String displayName, Role role, String password) {}

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no 0/o, 1/l/i
    private static final ObjectMapper JSON = new ObjectMapper();

    private DemoDataGenerator() {}

    public static void main(String[] args) {
        Path dir = Path.of(args.length > 0 && !args[0].startsWith("--") ? args[0] : "../demo").toAbsolutePath().normalize();
        boolean force = List.of(args).contains("--force");
        List<Account> accounts = generate(dir, "aster.example", new Random(), force);
        System.out.println("Wrote " + dir.resolve("CREDENTIALS.md") + ", credentials.env and seed.sql for "
                + accounts.size() + " accounts. Keep them out of git; rotate with --force.");
    }

    /** @param telemetry randomness for the scan telemetry only; passwords always use SecureRandom */
    public static List<Account> generate(Path dir, String domain, Random telemetry, boolean force) {
        if (!force && Files.exists(dir.resolve("credentials.env"))) {
            throw new IllegalStateException(dir.resolve("credentials.env")
                    + " exists; pass --force to rotate every demo password");
        }
        SecureRandom random = new SecureRandom();
        List<Account> accounts = List.of(
                new Account("ADMIN", "avery.lin@" + domain, "Avery Lin", Role.ADMIN, password(random)),
                new Account("EMPLOYEE", "priya.nair@" + domain, "Priya Nair", Role.EMPLOYEE, password(random)),
                new Account("COLLEAGUE", "marcus.okafor@" + domain, "Marcus Okafor", Role.EMPLOYEE, password(random)),
                new Account("JUDGE", "judge@" + domain, "Demo Judge", Role.ADMIN, password(random)),
                new Account("CONSUMER", "riley.chen@mail." + domain, "Riley Chen", Role.CONSUMER, password(random)));
        try {
            Files.createDirectories(dir);
            writeSecret(dir.resolve("credentials.env"), env(accounts));
            writeSecret(dir.resolve("CREDENTIALS.md"), markdown(accounts));
            Files.writeString(dir.resolve("seed.sql"), sql(domain, accounts, telemetry));
            Files.writeString(dir.resolve("rules.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(rules(accounts)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return accounts;
    }

    /** Four groups of five from an unambiguous alphabet: about 99 bits, easy to read out or type. */
    static String password(SecureRandom random) {
        StringBuilder out = new StringBuilder();
        for (int group = 0; group < 4; group++) {
            if (group > 0) {
                out.append('-');
            }
            for (int i = 0; i < 5; i++) {
                out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return out.toString();
    }

    private static void writeSecret(Path file, String content) throws IOException {
        Files.deleteIfExists(file);
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(file, content);
    }

    private static String env(List<Account> accounts) {
        StringBuilder out = new StringBuilder("# Official demo accounts. Generated; never commit. Rotate: scripts/build.sh demo --force\n");
        for (Account a : accounts) {
            out.append("DEMO_").append(a.key()).append("_EMAIL=").append(a.email()).append('\n');
            out.append("DEMO_").append(a.key()).append("_PASSWORD=").append(a.password()).append('\n');
        }
        return out.toString();
    }

    private static String markdown(List<Account> accounts) {
        StringBuilder out = new StringBuilder("""
                # Obsidian official demo credentials

                Generated by `scripts/build.sh demo`. Never commit, share or screenshot this file.
                Rotate every password with `scripts/build.sh demo --force`, then reload the data.

                | Who | Email | Password | Role | Used for |
                | --- | --- | --- | --- | --- |
                """);
        Map<String, String> uses = Map.of(
                "ADMIN", "Presenter: dashboard, rule editor, Bypass, agents, passkey sign-in",
                "EMPLOYEE", "The phone (Android): reports a link, crowd immunity",
                "COLLEAGUE", "A second employee whose reports block for Priya too",
                "JUDGE", "Hand this to judges who want to try the dashboard",
                "CONSUMER", "Consumer tier: personal rules only, no organization");
        for (Account a : accounts) {
            out.append("| ").append(a.displayName()).append(" | `").append(a.email()).append("` | `").append(a.password())
                    .append("` | ").append(a.role()).append(" | ").append(uses.get(a.key())).append(" |\n");
        }
        out.append("""

                ## Demo flow

                1. Sign in as Avery with a passkey: enroll one first under Settings → Add a passkey (it's bound to `RP_ID`).
                2. Email Security: GitHub / Google alerts pass SPF+DKIM+DMARC and are verified official; the payroll
                   phish and the invoice malware are flagged; the parcel phish passes all three checks but isn't
                   official (attacker-owned domain), and its link stays PENDING, never "clean".
                3. Report `testsafebrowsing.appspot.com` (left unreported on purpose) from the phone as Priya, or from
                   Rules and Policies → New rule. The daemon blocks it: `curl -m 5 http://testsafebrowsing.appspot.com/` fails.
                4. Bypass it as Avery: `curl` works again.

                Pre-seeded rules (rules.json, applied through RuleService when `run --demo` starts the backend): admin blocks for two TEST-NET IPs and
                invoice-verify.example, admin allows for docs/status.aster.example, colleague reports for a parcel
                phishing domain, a spam number, a sender, a URL and a malware hash, Priya's 2-hour personal bypass
                of delivery-status.example, and Riley's personal spam block.
                """);
        return out.toString();
    }

    /** The demo's threat rules, applied through RuleService as each account by the startup setup. */
    static List<Map<String, Object>> rules(List<Account> accounts) {
        Map<String, String> as = emails(accounts);
        String admin = as.get("ADMIN"), colleague = as.get("COLLEAGUE");
        return List.of(
                rule(admin, "REPORT", "IP", "203.0.113.45", "MALWARE", null),
                rule(admin, "REPORT", "IP", "198.51.100.23", "MALWARE", null),
                rule(admin, "REPORT", "DOMAIN", "invoice-verify.example", "MALWARE", null),
                rule(admin, "BYPASS", "DOMAIN", "docs.aster.example", null, null),
                rule(admin, "BYPASS", "DOMAIN", "status.aster.example", null, null),
                rule(colleague, "REPORT", "DOMAIN", "parcel-redelivery.example", "PHISHING", null),
                rule(colleague, "REPORT", "PHONE", "+15550142", "SPAM", null),
                rule(colleague, "REPORT", "EMAIL", "payroll@aster-payroll.example", "PHISHING", null),
                rule(colleague, "REPORT", "URL", "https://wallet-verify.example/connect", "PHISHING", null),
                rule(colleague, "REPORT", "FILE_HASH", "5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f",
                        "MALWARE", null),
                rule(as.get("EMPLOYEE"), "BYPASS", "DOMAIN", "delivery-status.example", null, 120),
                rule(as.get("CONSUMER"), "REPORT", "PHONE", "+15550177", "SPAM", null));
    }

    private static Map<String, Object> rule(String as, String action, String type, String value, String category,
            Integer ttlMinutes) {
        Map<String, Object> rule = new java.util.LinkedHashMap<>();
        rule.put("as", as);
        rule.put("action", action);
        rule.put("indicatorType", type);
        rule.put("indicatorValue", value);
        if (category != null) {
            rule.put("threatCategory", category);
        }
        if (ttlMinutes != null) {
            rule.put("ttlMinutes", ttlMinutes);
        }
        return rule;
    }

    // ---- seed.sql -------------------------------------------------------------------

    private record Indicator(String channel, String sender, String value, String verdict, int detections) {}

    private static String sql(String domain, List<Account> accounts, Random random) {
        PasswordEncoder encoder = SecurityBeans.argon2id();
        UUID org = uuid(domain, "org");
        StringBuilder out = new StringBuilder("""
                -- Official demo data, generated by scripts/build.sh demo. Idempotent: reloading it updates the
                -- accounts (and their passwords) and adds nothing twice. Timestamps are relative to NOW(), so the
                -- data looks fresh whenever it is loaded. Passwords are only stored as Argon2id hashes.
                -- Threat rules are created through the API (scripts/build.sh run --demo), never here.

                """);
        out.append("INSERT INTO organizations (id, name, policy_allow_user_bypass) VALUES (")
                .append(q(org)).append(", 'Aster Research', TRUE)\n")
                .append("ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, policy_allow_user_bypass = TRUE;\n\n");

        out.append("INSERT INTO users (id, org_id, email, display_name, role, password_hash) VALUES\n");
        for (int i = 0; i < accounts.size(); i++) {
            Account a = accounts.get(i);
            out.append("  (").append(q(uuid(domain, "user:" + a.key()))).append(", ")
                    .append(a.role() == Role.CONSUMER ? "NULL" : q(org)).append(", ").append(q(a.email())).append(", ")
                    .append(q(a.displayName())).append(", ").append(q(a.role().name())).append(", ")
                    .append(q(encoder.encode(a.password()))).append(i < accounts.size() - 1 ? "),\n" : ")\n");
        }
        out.append("ON CONFLICT (email) DO UPDATE SET org_id = EXCLUDED.org_id, display_name = EXCLUDED.display_name,\n"
                + "  role = EXCLUDED.role, password_hash = EXCLUDED.password_hash;\n\n");

        out.append(telemetry(domain, accounts, random));
        out.append(inbox(domain, emails(accounts).get("ADMIN"), "ADMIN"));
        out.append(inbox(domain, emails(accounts).get("EMPLOYEE"), "EMPLOYEE"));
        return out.toString();
    }

    /** About a day of scan verdicts across email, SMS, WhatsApp and the network, for the dashboard charts. */
    private static String telemetry(String domain, List<Account> accounts, Random random) {
        List<Indicator> pool = List.of(
                new Indicator("EMAIL", "payroll@aster-payroll.example", "http://login-verify.aster-payroll.example/reset", "MALICIOUS", 14),
                new Indicator("EMAIL", "billing@invoice-verify.example", "5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f", "MALICIOUS", 31),
                new Indicator("EMAIL", "security@secure-mfa-check.example", "https://secure-mfa-check.example/session", "MALICIOUS", 7),
                new Indicator("EMAIL", "noreply@parcel-redelivery.example", "http://parcel-redelivery.example/fee", "PENDING", 0),
                new Indicator("EMAIL", "it-support@aster.example", "https://status.aster.example", "CLEAN", 0),
                new Indicator("EMAIL", "people-ops@aster.example", "https://docs.aster.example/onboarding", "CLEAN", 0),
                new Indicator("EMAIL", "noreply@github.com", "https://github.com/settings/security", "CLEAN", 0),
                new Indicator("EMAIL", "news@vendor-digest.example", "https://vendor-digest.example/weekly", "SUSPICIOUS", 1),
                new Indicator("SMS", "+15550100", "http://delivery-status.example/track/88231", "BYPASSED", 0),
                new Indicator("SMS", "+15550142", "http://parcel-redelivery.example/fee", "MALICIOUS", 9),
                new Indicator("SMS", "+15550177", "https://wallet-verify.example/connect", "SUSPICIOUS", 2),
                new Indicator("SMS", "+15550163", null, "CLEAN", 0),
                new Indicator("WHATSAPP", "Marcus Okafor", "https://calendar.aster.example/event/88", "CLEAN", 0),
                new Indicator("WHATSAPP", "Unknown +15550188", "https://wallet-verify.example/connect", "MALICIOUS", 11),
                new Indicator("WHATSAPP", "Priya Nair", "https://docs.aster.example/onboarding", "CLEAN", 0),
                new Indicator("WHATSAPP", "Unknown +15550191", "http://gift-card-claim.example/redeem", "PENDING", 0),
                new Indicator("FIREWALL", "demo-laptop", "203.0.113.45", "MALICIOUS", 6),
                new Indicator("FIREWALL", "build-linux-01", "198.51.100.23", "MALICIOUS", 4),
                new Indicator("FIREWALL", "build-linux-01", "192.0.2.77", "SUSPICIOUS", 1));
        int[] weights = {6, 3, 2, 3, 9, 6, 5, 3, 3, 4, 3, 6, 5, 3, 5, 2, 3, 2, 2};
        String[] owners = {"ADMIN", "EMPLOYEE", "EMPLOYEE", "COLLEAGUE", "COLLEAGUE", "CONSUMER"};
        Map<String, String> emails = emails(accounts);
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        StringBuilder out = new StringBuilder(
                "INSERT INTO scan_logs (id, user_id, channel, sender, indicator_checked, verdict, vt_positives, created_at) VALUES\n");
        int rows = 140;
        for (int i = 0; i < rows; i++) {
            int pick = random.nextInt(total);
            int index = 0;
            while (pick >= weights[index]) {
                pick -= weights[index++];
            }
            Indicator indicator = pool.get(index);
            String owner = owners[random.nextInt(owners.length)];
            int minutesAgo = 5 + (int) (Math.pow(random.nextDouble(), 1.6) * 1435); // busier recently
            out.append("  (").append(q(uuid(domain, "scan:" + i))).append(", ").append(userId(emails.get(owner)))
                    .append(", ").append(q(indicator.channel())).append(", ").append(q(indicator.sender())).append(", ")
                    .append(indicator.value() == null ? "NULL" : q(indicator.value())).append(", ")
                    .append(q(indicator.verdict())).append(", ").append(indicator.detections())
                    .append(", NOW() - INTERVAL '").append(minutesAgo).append(" minutes'")
                    .append(i < rows - 1 ? "),\n" : ")\n");
        }
        return out.append("ON CONFLICT (id) DO NOTHING;\n\n").toString();
    }

    private record Mail(String id, String from, String sender, String subject, int minutesAgo, String spf, String dkim,
            String dmarc, boolean official, String verdict, List<Map<String, Object>> links,
            List<Map<String, Object>> attachments) {}

    private static Map<String, Object> link(String url, String verdict, int detections) {
        return Map.of("url", url, "verdict", verdict, "vtDetections", detections);
    }

    /** The Email security page: official mail left alone, phishing and malware flagged, an unknown link PENDING. */
    private static String inbox(String domain, String ownerEmail, String owner) {
        List<Mail> mails = List.of(
                new Mail("gh-signin", "GitHub <noreply@github.com>", "noreply@github.com",
                        "[GitHub] A new sign-in to your account", 18, "PASS", "PASS", "PASS", true, "CLEAN",
                        List.of(link("https://github.com/settings/security", "CLEAN", 0)), List.of()),
                new Mail("google-alert", "Google <no-reply@accounts.google.com>", "no-reply@accounts.google.com",
                        "Security alert: new device signed in", 47, "PASS", "PASS", "PASS", true, "CLEAN",
                        List.of(link("https://myaccount.google.com/notifications", "CLEAN", 0)), List.of()),
                new Mail("payroll-phish", "Payroll Team <payroll@aster-payroll.example>", "payroll@aster-payroll.example",
                        "Action required: confirm your salary account", 65, "FAIL", "NONE", "FAIL", false, "MALICIOUS",
                        List.of(link("http://login-verify.aster-payroll.example/reset", "MALICIOUS", 14)), List.of()),
                new Mail("parcel-phish", "Courier Updates <noreply@parcel-redelivery.example>",
                        "noreply@parcel-redelivery.example", "Your parcel is held: pay the redelivery fee", 131,
                        "PASS", "PASS", "PASS", false, "PENDING",
                        List.of(link("http://parcel-redelivery.example/fee", "PENDING", 0)), List.of()),
                new Mail("invoice-malware", "Accounts <billing@invoice-verify.example>", "billing@invoice-verify.example",
                        "Invoice #4471 is overdue", 256, "PASS", "PASS", "FAIL", false, "MALICIOUS", List.of(),
                        List.of(Map.of("filename", "invoice-4471.pdf.exe", "sha256",
                                "5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f", "verdict", "MALICIOUS",
                                "vtDetections", 31))),
                new Mail("maintenance", "IT Service Desk <it-support@aster.example>", "it-support@aster.example",
                        "Scheduled maintenance tonight, 22:00 UTC", 380, "PASS", "PASS", "PASS", false, "CLEAN",
                        List.of(link("https://status.aster.example", "CLEAN", 0)), List.of()),
                new Mail("planning", "Marcus Okafor <marcus.okafor@" + domain + ">", "marcus.okafor@" + domain,
                        "Q3 planning notes", 610, "PASS", "PASS", "PASS", false, "CLEAN", List.of(), List.of()));
        StringBuilder out = new StringBuilder("INSERT INTO scanned_emails (id, user_id, gmail_message_id, from_header, "
                + "sender_address, subject, received_at, spf, dkim, dmarc, verified_official, verdict, links, attachments) VALUES\n");
        for (int i = 0; i < mails.size(); i++) {
            Mail m = mails.get(i);
            out.append("  (").append(q(uuid(domain, "mail:" + owner + ":" + m.id()))).append(", ")
                    .append(userId(ownerEmail)).append(", ").append(q("demo-" + m.id())).append(", ")
                    .append(q(m.from())).append(", ").append(q(m.sender())).append(", ").append(q(m.subject()))
                    .append(", NOW() - INTERVAL '").append(m.minutesAgo()).append(" minutes', ").append(q(m.spf()))
                    .append(", ").append(q(m.dkim())).append(", ").append(q(m.dmarc())).append(", ").append(m.official())
                    .append(", ").append(q(m.verdict())).append(", ").append(q(json(m.links()))).append("::jsonb, ")
                    .append(q(json(m.attachments()))).append("::jsonb").append(i < mails.size() - 1 ? "),\n" : ")\n");
        }
        return out.append("ON CONFLICT (user_id, gmail_message_id) DO NOTHING;\n\n").toString();
    }

    private static Map<String, String> emails(List<Account> accounts) {
        return accounts.stream().collect(java.util.stream.Collectors.toMap(Account::key, Account::email));
    }

    /** By email, so the rows attach to the account even if it existed before with another id. */
    private static String userId(String email) {
        return "(SELECT id FROM users WHERE email = " + q(email) + ")";
    }

    private static UUID uuid(String domain, String name) {
        return UUID.nameUUIDFromBytes((domain + "/" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String q(Object value) {
        return "'" + value.toString().replace("'", "''") + "'";
    }

}
