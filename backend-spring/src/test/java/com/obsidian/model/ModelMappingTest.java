package com.obsidian.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.obsidian.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The entities run with ddl-auto=validate against db/init.sql, so a column or
 * type mismatch fails the context. These tests also check what lands in the
 * columns, read back with plain SQL rather than through JPA.
 */
class ModelMappingTest extends IntegrationTest {

    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired EndpointAgentRepository agents;
    @Autowired ScanLogRepository scanLogs;
    @Autowired AuditLogRepository auditLogs;
    @Autowired ScannedEmailRepository scannedEmails;
    @Autowired JdbcTemplate jdbc;

    @Test
    void organizationDefaultsAllowUserBypass() {
        Organization org = organizations.save(new Organization("Aster Research"));

        Map<String, Object> row = jdbc.queryForMap(
                "select name, policy_allow_user_bypass, created_at from organizations where id = ?", org.getId());

        assertThat(row.get("name")).isEqualTo("Aster Research");
        assertThat(row.get("policy_allow_user_bypass")).isEqualTo(true);
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void userRoleIsStoredAsText() {
        Organization org = organizations.save(new Organization("Mapping Org"));
        User user = new User("mapping-admin@aster.example", Role.ADMIN);
        user.setOrgId(org.getId());
        user.setDisplayName("Mapping Admin");
        users.save(user);

        Map<String, Object> row = jdbc.queryForMap(
                "select role, org_id, display_name, totp_enabled from users where email = ?", "mapping-admin@aster.example");

        assertThat(row.get("role")).isEqualTo("ADMIN");
        assertThat(row.get("org_id")).isEqualTo(org.getId());
        assertThat(row.get("display_name")).isEqualTo("Mapping Admin");
        assertThat(row.get("totp_enabled")).isEqualTo(false);
        assertThat(users.findByEmail("mapping-admin@aster.example")).get()
                .extracting(User::getRole).isEqualTo(Role.ADMIN);
    }

    @Test
    void scannedEmailLinksUseApiFieldNamesInJsonb() {
        User user = users.save(new User("mapping-mail@aster.example", Role.CONSUMER));
        ScannedEmail email = new ScannedEmail(user.getId(), "18c2f0a1b2c3d4e5");
        email.setFromHeader("Payroll Team <payroll@aster-payroll.example>");
        email.setSenderAddress("payroll@aster-payroll.example");
        email.setSubject("Action required");
        email.setReceivedAt(Instant.parse("2026-10-27T09:10:00Z"));
        email.setSpf(AuthCheck.FAIL);
        email.setDkim(AuthCheck.NONE);
        email.setDmarc(AuthCheck.FAIL);
        email.setVerdict(Verdict.MALICIOUS);
        email.setLinks(List.of(new EmailLink("http://login-verify.aster-payroll.example/reset", Verdict.MALICIOUS, 14)));
        email.setAttachments(List.of(new EmailAttachment("invoice.pdf.exe",
                "5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f", Verdict.PENDING, 0)));
        scannedEmails.save(email);

        Map<String, Object> row = jdbc.queryForMap("""
                select links -> 0 ->> 'url' as url, links -> 0 ->> 'verdict' as verdict,
                       links -> 0 ->> 'vtDetections' as detections, attachments -> 0 ->> 'sha256' as sha256,
                       spf, dmarc
                from scanned_emails where gmail_message_id = '18c2f0a1b2c3d4e5'""");

        assertThat(row).containsEntry("url", "http://login-verify.aster-payroll.example/reset")
                .containsEntry("verdict", "MALICIOUS")
                .containsEntry("detections", "14")
                .containsEntry("sha256", "5f2b8a0c94d1e3b7a6c9f0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4f")
                .containsEntry("spf", "FAIL")
                .containsEntry("dmarc", "FAIL");
        assertThat(scannedEmails.existsByUserIdAndGmailMessageId(user.getId(), "18c2f0a1b2c3d4e5")).isTrue();
    }

    @Test
    void auditDetailsAreJsonb() {
        AuditLog log = new AuditLog(AuditAction.REPORT, null, null, null,
                Map.of("indicatorType", "DOMAIN", "indicatorValue", "login-verify.example"));
        auditLogs.save(log);

        String value = jdbc.queryForObject(
                "select details ->> 'indicatorValue' from audit_logs where id = ?", String.class, log.getId());

        assertThat(value).isEqualTo("login-verify.example");
    }

    @Test
    void agentAndScanLogRoundTrip() {
        Organization org = organizations.save(new Organization("Agent Org"));
        EndpointAgent agent = agents.save(new EndpointAgent(org.getId(), "build-linux-01", "a".repeat(64)));
        ScanLog log = new ScanLog(null, Channel.WHATSAPP, "Priya Nair", "http://testsafebrowsing.appspot.com/s/phishing.html",
                Verdict.BYPASSED, 0);
        scanLogs.save(log);

        assertThat(agents.findByTokenHash("a".repeat(64))).get().extracting(EndpointAgent::getHostname)
                .isEqualTo("build-linux-01");
        assertThat(jdbc.queryForObject("select verdict from scan_logs where id = ?", String.class, log.getId()))
                .isEqualTo("BYPASSED");
        assertThat(agent.getCreatedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
    }
}
