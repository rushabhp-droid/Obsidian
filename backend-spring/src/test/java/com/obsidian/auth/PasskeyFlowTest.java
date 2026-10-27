package com.obsidian.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestAccounts;
import com.obsidian.support.TestAccounts.Team;
import com.obsidian.support.WebSession;
import com.webauthn4j.data.AttestationConveyancePreference;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.EmulatorUtil;
import com.webauthn4j.test.authenticator.webauthn.PackedAuthenticator;
import com.webauthn4j.test.client.ClientPlatform;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A full passkey ceremony against Spring Security's endpoints, with an
 * emulated FIDO2 authenticator and the JSON bodies web-dashboard/src/lib/webauthn.ts
 * sends: enroll while signed in, then sign in with the passkey alone.
 */
@AutoConfigureMockMvc
class PasskeyFlowTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    final ClientPlatform browser = EmulatorUtil.createClientPlatform(new PackedAuthenticator());

    PasskeyFlowTest() {
        browser.setOrigin(new Origin("http://localhost:3000"));
    }

    static byte[] decode(String base64url) {
        return Base64.getUrlDecoder().decode(base64url);
    }

    static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Test
    void enrolledPasskeySignsInWithoutAPassword() throws Exception {
        Team team = accounts.team(true);
        String email = team.admin().email();
        enroll(WebSession.signIn(mvc, email, TestAccounts.PASSWORD), "Test laptop");

        assertThat(jdbc.queryForObject("select name from user_entities where name = ?", String.class, email))
                .isEqualTo(email);
        assertThat(jdbc.queryForObject("""
                select c.label from user_credentials c join user_entities e on e.id = c.user_entity_user_id
                where e.name = ?""", String.class, email)).isEqualTo("Test laptop");

        WebSession fresh = new WebSession();
        MockHttpServletResponse optionsResponse = mvc.perform(post("/webauthn/authenticate/options"))
                .andExpect(status().isOk()).andReturn().getResponse();
        fresh.remember(optionsResponse);
        JsonNode options = mapper.readTree(optionsResponse.getContentAsString());

        PublicKeyCredential<AuthenticatorAssertionResponse, ?> assertion = browser.get(new PublicKeyCredentialRequestOptions(
                new DefaultChallenge(decode(options.get("challenge").asText())), options.get("timeout").asLong(),
                options.get("rpId").asText(), List.of(), UserVerificationRequirement.PREFERRED, null));

        AuthenticatorAssertionResponse response = assertion.getResponse();
        ObjectNode body = mapper.createObjectNode();
        body.put("id", assertion.getId());
        body.put("rawId", encode(assertion.getRawId()));
        ObjectNode responseJson = body.putObject("response");
        responseJson.put("authenticatorData", encode(response.getAuthenticatorData()));
        responseJson.put("clientDataJSON", encode(response.getClientDataJSON()));
        responseJson.put("signature", encode(response.getSignature()));
        responseJson.put("userHandle", encode(response.getUserHandle()));
        body.put("credType", "public-key");
        body.putObject("clientExtensionResults");
        body.put("authenticatorAttachment", "platform");

        MockHttpServletResponse login = mvc.perform(fresh.cookies(post("/login/webauthn")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn().getResponse();
        fresh.remember(login);

        mvc.perform(fresh.cookies(get("/api/v1/auth/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void startingAPasskeyLoginRotatesTheSessionId() throws Exception {
        WebSession anonymous = new WebSession();
        anonymous.remember(mvc.perform(post("/webauthn/authenticate/options")).andReturn().getResponse());
        String before = anonymous.sessionCookie().getValue();

        anonymous.remember(mvc.perform(anonymous.cookies(post("/webauthn/authenticate/options")))
                .andExpect(status().isOk()).andReturn().getResponse());

        assertThat(anonymous.sessionCookie().getValue()).isNotEqualTo(before);
    }

    private void enroll(WebSession session, String label) throws Exception {
        JsonNode options = mapper.readTree(mvc.perform(session.withCsrf(post("/webauthn/register/options")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        List<PublicKeyCredentialParameters> algorithms = new ArrayList<>();
        options.get("pubKeyCredParams").forEach(p -> algorithms.add(new PublicKeyCredentialParameters(
                PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.create(p.get("alg").asLong()))));
        JsonNode user = options.get("user");
        JsonNode selection = options.path("authenticatorSelection");
        PublicKeyCredential<AuthenticatorAttestationResponse, ?> credential = browser.create(
                new PublicKeyCredentialCreationOptions(
                        new PublicKeyCredentialRpEntity(options.get("rp").get("id").asText(), options.get("rp").get("name").asText()),
                        new PublicKeyCredentialUserEntity(decode(user.get("id").asText()), user.get("name").asText(),
                                user.get("displayName").asText()),
                        new DefaultChallenge(decode(options.get("challenge").asText())),
                        algorithms,
                        options.path("timeout").asLong(300_000),
                        List.of(),
                        new AuthenticatorSelectionCriteria(null,
                                ResidentKeyRequirement.create(selection.path("residentKey").asText("required")),
                                UserVerificationRequirement.create(selection.path("userVerification").asText("preferred"))),
                        AttestationConveyancePreference.NONE,
                        null));

        ObjectNode body = mapper.createObjectNode();
        ObjectNode credentialJson = body.putObject("publicKey").putObject("credential");
        credentialJson.put("id", credential.getId());
        credentialJson.put("rawId", encode(credential.getRawId()));
        ObjectNode response = credentialJson.putObject("response");
        response.put("attestationObject", encode(credential.getResponse().getAttestationObject()));
        response.put("clientDataJSON", encode(credential.getResponse().getClientDataJSON()));
        response.putArray("transports");
        credentialJson.put("type", "public-key");
        credentialJson.putObject("clientExtensionResults");
        credentialJson.put("authenticatorAttachment", "platform");
        ((ObjectNode) body.get("publicKey")).put("label", label);

        mvc.perform(session.withCsrf(post("/webauthn/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
