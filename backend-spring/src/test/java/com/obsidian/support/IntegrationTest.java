package com.obsidian.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Full application context against the shared embedded Postgres and Redis. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        TestInfra.start();
        registry.add("spring.datasource.url", TestInfra::jdbcUrl);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.data.redis.host", TestInfra::redisHost);
        registry.add("spring.data.redis.port", TestInfra::redisPort);
    }
}
