package com.obsidian.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.model.Account;
import com.obsidian.model.Channel;
import com.obsidian.model.IndicatorType;
import com.obsidian.model.ThreatCategory;
import com.obsidian.model.Verdict;
import com.obsidian.rules.RuleService;
import com.obsidian.scanner.ScanResponse.IndicatorResult;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.RedisEvents;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** VirusTotal is the external system: stubbed per test. Rules, Postgres and Redis are real. */
class ScanServiceTest extends IntegrationTest {

    @Autowired ScanService scans;
    @Autowired RuleService rules;
    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired RedisConnectionFactory redis;
    @Autowired ObjectMapper mapper;
    @MockitoBean VirusTotalClient virusTotal;

    Team team;
    RedisEvents events;

    static final VtVerdict PENDING = VtVerdict.pending();
    static final VtVerdict CLEAN = VtVerdict.fromStats(0, 0, ThreatCategory.PHISHING, Instant.now());

    @BeforeEach
    void setUp() {
        team = accounts.team(true);
        events = new RedisEvents(redis, mapper);
        given(virusTotal.lookupUrl(anyString())).willReturn(CLEAN);
        given(virusTotal.lookupFile(anyString())).willReturn(CLEAN);
    }

    @AfterEach
    void tearDown() throws Exception {
        events.close();
    }

    static String host(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8) + ".example";
    }

    static ScanRequest sms(String sender, String text, List<String> urls) {
        return new ScanRequest(Channel.SMS, sender, false, text, urls, List.of());
    }

    @Test
    void colleaguesReportedDomainFlagsTheLinkWithoutAskingVirusTotal() {
        String domain = host("parcel-held");
        rules.report(team.colleague(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING);
        String url = "http://" + domain + "/track/88231";

        ScanResponse response = scans.scan(team.employee(), sms("+15550100", null, List.of(url)));

        assertThat(response.verdict()).isEqualTo(Verdict.MALICIOUS);
        assertThat(response.category()).isEqualTo(ThreatCategory.PHISHING);
        IndicatorResult link = response.indicators().stream()
                .filter(i -> i.indicatorType() == IndicatorType.URL).findFirst().orElseThrow();
        assertThat(link.source()).isEqualTo(ScanResponse.Source.RULE);
        assertThat(link.ruleId()).isNotNull();
        verify(virusTotal, never()).lookupUrl(anyString());
    }

    @Test
    void exactUrlBypassBeatsTheDomainBlock() {
        String domain = host("shared-host");
        String url = "https://" + domain + "/known-good";
        rules.report(team.admin(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING);
        rules.bypass(team.admin(), IndicatorType.URL, url, null);

        ScanResponse response = scans.scan(team.employee(), sms("Courier", null, List.of(url)));

        assertThat(response.verdict()).isEqualTo(Verdict.CLEAN);
        assertThat(jdbc.queryForObject("select verdict from scan_logs where id = ?", String.class, response.scanId()))
                .isEqualTo("BYPASSED");
    }

    @Test
    void unknownLinkGoesToVirusTotal() {
        String url = "http://" + host("fresh-phish") + "/login";
        given(virusTotal.lookupUrl(url)).willReturn(VtVerdict.fromStats(14, 1, ThreatCategory.PHISHING, Instant.now()));

        ScanResponse response = scans.scan(team.employee(), sms("+15550199", null, List.of(url)));

        assertThat(response.verdict()).isEqualTo(Verdict.MALICIOUS);
        assertThat(response.vtDetections()).isEqualTo(14);
        assertThat(response.indicators()).singleElement().satisfies(link -> {
            assertThat(link.source()).isEqualTo(ScanResponse.Source.VIRUSTOTAL);
            assertThat(link.value()).isEqualTo(url);
        });
    }

    @Test
    void pendingIsNeverReportedClean() {
        String pending = "https://" + host("unknown") + "/a";
        String clean = "https://" + host("known") + "/b";
        given(virusTotal.lookupUrl(pending)).willReturn(PENDING);

        ScanResponse response = scans.scan(team.employee(), sms("+15550199", null, List.of(clean, pending)));

        assertThat(response.verdict()).isEqualTo(Verdict.PENDING);
        assertThat(response.category()).isNull();
    }

    @Test
    void theWorstIndicatorDecides() {
        String suspicious = "https://" + host("odd") + "/a";
        given(virusTotal.lookupUrl(suspicious)).willReturn(VtVerdict.fromStats(2, 0, ThreatCategory.PHISHING, Instant.now()));

        ScanResponse response = scans.scan(team.employee(),
                sms("+15550199", null, List.of("https://" + host("fine") + "/", suspicious)));

        assertThat(response.verdict()).isEqualTo(Verdict.SUSPICIOUS);
        assertThat(jdbc.queryForObject("select indicator_checked from scan_logs where id = ?", String.class,
                response.scanId())).isEqualTo(suspicious);
    }

    @Test
    void savedContactsMayOnlySendLinks() {
        ScanRequest leaky = new ScanRequest(Channel.WHATSAPP, "Priya Nair", true, "see you at 6", List.of(), List.of());

        assertThatThrownBy(() -> scans.scan(team.employee(), leaky)).isInstanceOf(InvalidScanRequestException.class);
    }

    @Test
    void linksInAnUnknownSendersTextAreScanned() {
        String domain = host("pay-now");
        rules.report(team.colleague(), IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING);

        ScanResponse response = scans.scan(team.employee(),
                sms("+15550188", "Final notice, pay at http://" + domain + "/pay today.", List.of()));

        assertThat(response.verdict()).isEqualTo(Verdict.MALICIOUS);
    }

    @Test
    void reportedPhoneNumberFlagsItsTexts() {
        Account consumer = accounts.consumer();
        rules.report(consumer, IndicatorType.PHONE, "+15550177", ThreatCategory.SPAM);

        ScanResponse response = scans.scan(consumer, sms("+1 555 0177", "hello", List.of()));

        assertThat(response.verdict()).isEqualTo(Verdict.MALICIOUS);
        assertThat(response.category()).isEqualTo(ThreatCategory.SPAM);
    }

    @Test
    void textWithoutLinksIsCleanAndStillLogged() {
        ScanResponse response = scans.scan(team.employee(), sms("Unknown", "Are you free for a call?", List.of()));

        assertThat(response.verdict()).isEqualTo(Verdict.CLEAN);
        assertThat(response.indicators()).isEmpty();
        Map<String, Object> row = jdbc.queryForMap("select verdict, indicator_checked, channel from scan_logs where id = ?",
                response.scanId());
        assertThat(row).containsEntry("verdict", "CLEAN").containsEntry("indicator_checked", null)
                .containsEntry("channel", "SMS");
    }

    @Test
    void firewallIsNotAScanChannel() {
        ScanRequest request = new ScanRequest(Channel.FIREWALL, "host", false, null, List.of("https://a.example"), List.of());

        assertThatThrownBy(() -> scans.scan(team.employee(), request)).isInstanceOf(InvalidScanRequestException.class);
    }

    @Test
    void malformedFileHashIsRejected() {
        ScanRequest request = new ScanRequest(Channel.EMAIL, "a@b.example", false, null, List.of(), List.of("not-a-hash"));

        assertThatThrownBy(() -> scans.scan(team.employee(), request)).isInstanceOf(InvalidScanRequestException.class);
    }

    @Test
    void everyScanIsLoggedAndPublishedToTheDashboard() {
        String url = "http://" + host("feed") + "/x";
        given(virusTotal.lookupUrl(url)).willReturn(VtVerdict.fromStats(9, 0, ThreatCategory.PHISHING, Instant.now()));

        ScanResponse response = scans.scan(team.employee(), new ScanRequest(Channel.WHATSAPP, "Priya Nair", true, null,
                List.of(url), List.of()));

        JsonNode envelope = events.await(e -> e.path("kind").asText().equals("SCAN")
                && e.path("event").path("scan").path("id").asText().equals(response.scanId().toString()));
        assertThat(envelope.path("orgId").asText()).isEqualTo(team.org().getId().toString());
        assertThat(envelope.path("userId").asText()).isEqualTo(team.employee().userId().toString());
        JsonNode scan = envelope.path("event").path("scan");
        assertThat(envelope.path("event").path("type").asText()).isEqualTo("SCAN");
        assertThat(scan.path("channel").asText()).isEqualTo("WHATSAPP");
        assertThat(scan.path("sender").asText()).isEqualTo("Priya Nair");
        assertThat(scan.path("indicatorChecked").asText()).isEqualTo(url);
        assertThat(scan.path("verdict").asText()).isEqualTo("MALICIOUS");
        assertThat(scan.path("vtPositives").asInt()).isEqualTo(9);
    }
}
