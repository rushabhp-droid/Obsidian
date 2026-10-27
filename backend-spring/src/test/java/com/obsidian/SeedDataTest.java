package com.obsidian;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.obsidian.support.IntegrationTest;
import com.obsidian.support.TestInfra;
import com.obsidian.support.WebSession;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** scripts/seed.sql loads, twice, and its demo password works with the app's Argon2 settings. */
@AutoConfigureMockMvc
class SeedDataTest extends IntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void seededAdminSignsInWithTheDemoPassword() throws Exception {
        TestInfra.applySchema(Path.of("..", "scripts", "seed.sql"));
        TestInfra.applySchema(Path.of("..", "scripts", "seed.sql"));

        WebSession avery = WebSession.signIn(mvc, "avery.lin@aster.example", "obsidian-demo-2026");

        mvc.perform(avery.cookies(get("/api/v1/auth/me")))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.organization.name").value("Aster Research"));
        mvc.perform(avery.cookies(get("/api/v1/scans"))).andExpect(jsonPath("$.length()").value(5));
    }
}
