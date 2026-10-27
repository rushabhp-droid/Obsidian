package com.obsidian.firewall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obsidian.auth.Tokens;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AgentControllerTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    Team team;
    WebSession admin;

    @BeforeEach
    void signIn() throws Exception {
        team = accounts.team(true);
        admin = WebSession.signIn(mvc, team.admin().email(), TestAccounts.PASSWORD);
    }

    @Test
    void adminRegistersAnAgentAndSeesItsTokenOnce() throws Exception {
        JsonNode agent = mapper.readTree(mvc.perform(admin.withCsrf(post("/api/v1/agents"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hostname\":\"Build-Linux-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hostname").value("build-linux-01"))
                .andExpect(jsonPath("$.lastHeartbeatAt").isEmpty())
                .andReturn().getResponse().getContentAsString());

        String token = agent.get("token").asText();
        assertThat(token).matches("obs_[0-9a-f]{64}");
        assertThat(jdbc.queryForObject("select token_hash from endpoint_agents where id = ?::uuid", String.class,
                agent.get("id").asText())).isEqualTo(Tokens.sha256Hex(token));
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'AGENT_REGISTER' and org_id = ?",
                Integer.class, team.org().getId())).isEqualTo(1);
        mvc.perform(admin.cookies(get("/api/v1/agents")))
                .andExpect(jsonPath("$[0].id").value(agent.get("id").asText()))
                .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    void onlyAdminsRegisterAgents() throws Exception {
        WebSession employee = WebSession.signIn(mvc, team.employee().email(), TestAccounts.PASSWORD);

        mvc.perform(employee.withCsrf(post("/api/v1/agents")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostname\":\"laptop\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void hostnameIsValidated() throws Exception {
        mvc.perform(admin.withCsrf(post("/api/v1/agents")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hostname\":\"bad host; rm -rf\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consumersHaveNoAgents() throws Exception {
        var consumer = accounts.consumer();
        WebSession browser = WebSession.signIn(mvc, consumer.email(), TestAccounts.PASSWORD);

        mvc.perform(browser.cookies(get("/api/v1/agents"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
