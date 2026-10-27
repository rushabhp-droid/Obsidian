package com.obsidian;

import com.obsidian.support.TestInfra;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;

/**
 * Runs the backend on :8080 against an embedded PostgreSQL 16 and an in-process
 * Redis, seeded with scripts/seed.sql (or $OBSIDIAN_SEED_FILE), so no Docker is needed:
 * {@code cd backend-spring && gradle bootTestRun}. Data is gone when it stops.
 */
public final class DevServer {

    private DevServer() {}

    public static void main(String[] args) {
        TestInfra.start();
        // OBSIDIAN_SEED_FILE: another seed instead, e.g. the official demo (scripts/build.sh run --demo).
        String seed = System.getenv("OBSIDIAN_SEED_FILE");
        TestInfra.applySchema(seed == null || seed.isBlank() ? Path.of("..", "scripts", "seed.sql") : Path.of(seed));
        System.setProperty("spring.datasource.url", TestInfra.jdbcUrl());
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        System.setProperty("spring.data.redis.host", TestInfra.redisHost());
        System.setProperty("spring.data.redis.port", String.valueOf(TestInfra.redisPort()));
        if (System.getProperty("obsidian.enc-key") == null && System.getenv("APP_ENC_KEY") == null) {
            System.setProperty("obsidian.enc-key", "dev-only-encryption-key");
        }
        SpringApplication.run(ObsidianApplication.class, args);
    }
}
