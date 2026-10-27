package com.obsidian.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class SecurityBeans {

    @Bean
    PasswordEncoder passwordEncoder() {
        return argon2id();
    }

    /** Argon2id: 16-byte salt, 32-byte hash, parallelism 1, 19 MiB, 2 iterations. */
    public static PasswordEncoder argon2id() {
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
