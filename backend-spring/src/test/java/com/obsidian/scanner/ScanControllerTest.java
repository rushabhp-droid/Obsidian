package com.obsidian.scanner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.model.ThreatCategory;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ScanControllerTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper mapper;
    @MockitoBean VirusTotalClient virusTotal;

    Team team;

    @BeforeEach
    void setUp() {
        team = accounts.team(true);
        given(virusTotal.lookupUrl(anyString()))
                .willReturn(VtVerdict.fromStats(9, 0, ThreatCategory.PHISHING, Instant.now()));
    }

    String bearer(String email) throws Exception {
        return "Bearer " + mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + TestAccounts.PASSWORD
                                + "\",\"client\":\"MOBILE\"}"))
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void phoneScansAWhatsAppLinkFromASavedContact() throws Exception {
        mvc.perform(post("/api/v1/scan/unified").header("Authorization", bearer(team.employee().email()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"WHATSAPP","sender":"Priya Nair","isSavedContact":true,
                                 "urls":["http://testsafebrowsing.appspot.com/s/phishing.html"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.category").value("PHISHING"))
                .andExpect(jsonPath("$.vtDetections").value(9))
                .andExpect(jsonPath("$.scanId").isNotEmpty())
                .andExpect(jsonPath("$.indicators[0].indicatorType").value("URL"))
                .andExpect(jsonPath("$.indicators[0].source").value("VIRUSTOTAL"));
    }

    @Test
    void savedContactTextIsRejected() throws Exception {
        mvc.perform(post("/api/v1/scan/unified").header("Authorization", bearer(team.employee().email()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"WHATSAPP\",\"sender\":\"Priya\",\"isSavedContact\":true,\"text\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void expiredPhoneTokenIs401SoTheAppSignsInAgain() throws Exception {
        mvc.perform(post("/api/v1/scan/unified").header("Authorization", "Bearer obm_" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"SMS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminsSeeTheirOrganizationsScansAndEmployeesTheirOwn() throws Exception {
        String scan = "{\"channel\":\"SMS\",\"sender\":\"+15550100\",\"isSavedContact\":false,"
                + "\"urls\":[\"http://parcel.example/track\"]}";
        mvc.perform(post("/api/v1/scan/unified").header("Authorization", bearer(team.employee().email()))
                .contentType(MediaType.APPLICATION_JSON).content(scan)).andExpect(status().isOk());

        WebSession admin = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);
        WebSession colleague = WebSession.signIn(mvc, team.colleague().email(), TestAccounts.PASSWORD);

        mvc.perform(admin.cookies(get("/api/v1/scans")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].channel").value("SMS"))
                .andExpect(jsonPath("$[0].sender").value("+15550100"))
                .andExpect(jsonPath("$[0].indicatorChecked").value("http://parcel.example/track"))
                .andExpect(jsonPath("$[0].verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$[0].vtPositives").value(9));
        mvc.perform(colleague.cookies(get("/api/v1/scans")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
