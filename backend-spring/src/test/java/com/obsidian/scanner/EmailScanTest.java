package com.obsidian.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.obsidian.model.AuthCheck;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ScannedEmail;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
import com.obsidian.rules.RuleService;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Raw Gmail messages through ScanService: verified official mail is left alone, the rest is scanned. */
class EmailScanTest extends IntegrationTest {

    @Autowired ScanService scans;
    @Autowired RuleService rules;
    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean VirusTotalClient virusTotal;

    Team team;

    @BeforeEach
    void setUp() {
        team = accounts.team(true);
        given(virusTotal.lookupUrl(anyString())).willReturn(VtVerdict.fromStats(0, 0, ThreatCategory.PHISHING, Instant.now()));
        given(virusTotal.lookupFile(anyString())).willReturn(VtVerdict.pending());
    }

    static String messageId() {
        return "18c" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }

    @Test
    void verifiedOfficialEmailSkipsDeepInspection() throws IOException {
        ScannedEmail email = scans.scanEmail(team.employee(), messageId(), MimeEmailParserTest.fixture("official.eml"));

        assertThat(email.isVerifiedOfficial()).isTrue();
        assertThat(email.getVerdict()).isEqualTo(Verdict.CLEAN);
        assertThat(email.getSpf()).isEqualTo(AuthCheck.PASS);
        assertThat(email.getLinks()).singleElement().satisfies(link -> assertThat(link.verdict()).isEqualTo(Verdict.CLEAN));
        verify(virusTotal, never()).lookupUrl(anyString());
    }

    @Test
    void unverifiedEmailHasItsLinksAndAttachmentsScanned() throws IOException {
        given(virusTotal.lookupUrl("http://login-verify.aster-payroll.example/reset"))
                .willReturn(VtVerdict.fromStats(14, 0, ThreatCategory.PHISHING, Instant.now()));
        String id = messageId();

        ScannedEmail email = scans.scanEmail(team.employee(), id, MimeEmailParserTest.fixture("phishing.eml"));

        assertThat(email.isVerifiedOfficial()).isFalse();
        assertThat(email.getSpf()).isEqualTo(AuthCheck.FAIL);
        assertThat(email.getDkim()).isEqualTo(AuthCheck.NONE);
        assertThat(email.getVerdict()).isEqualTo(Verdict.MALICIOUS);
        assertThat(email.getLinks()).extracting(l -> l.url() + " " + l.verdict() + " " + l.vtDetections())
                .containsExactly("http://login-verify.aster-payroll.example/reset MALICIOUS 14",
                        "https://docs.example.org/help CLEAN 0");
        assertThat(email.getAttachments()).singleElement().satisfies(file -> assertThat(file.verdict()).isEqualTo(Verdict.PENDING));
        assertThat(jdbc.queryForObject("select count(*) from scanned_emails where gmail_message_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select verdict from scan_logs where channel = 'EMAIL' and sender = ?"
                + " order by created_at desc limit 1", String.class, "payroll@aster-payroll.example")).isEqualTo("MALICIOUS");
    }

    @Test
    void reportedSenderDomainFlagsTheEmail() throws IOException {
        rules.report(team.colleague(), IndicatorType.DOMAIN, "aster-payroll.example", ThreatCategory.PHISHING);

        ScannedEmail email = scans.scanEmail(team.employee(), messageId(), MimeEmailParserTest.fixture("phishing.eml"));

        assertThat(email.getVerdict()).isEqualTo(Verdict.MALICIOUS);
    }

    @Test
    void passingAuthFromAnUnlistedDomainIsNotOfficial() throws IOException {
        byte[] raw = new String(MimeEmailParserTest.fixture("official.eml"), java.nio.charset.StandardCharsets.UTF_8)
                .replace("github.com", "github-security.example").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ScannedEmail email = scans.scanEmail(team.employee(), messageId(), raw);

        assertThat(email.getSpf()).isEqualTo(AuthCheck.PASS);
        assertThat(email.isVerifiedOfficial()).isFalse();
    }

    @Test
    void whitelistedSenderWithPassingAuthCountsAsOfficial() throws IOException {
        byte[] raw = new String(MimeEmailParserTest.fixture("official.eml"), java.nio.charset.StandardCharsets.UTF_8)
                .replace("github.com", "status.aster.example").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        rules.bypass(team.admin(), IndicatorType.EMAIL, "noreply@status.aster.example", null);

        ScannedEmail email = scans.scanEmail(team.employee(), messageId(), raw);

        assertThat(email.isVerifiedOfficial()).isTrue();
    }
}
