package com.obsidian.scanner;

import com.obsidian.config.ObsidianProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ScannerConfig {

    @Bean
    VtRateLimiter vtRateLimiter(ObsidianProperties properties, Clock clock) {
        return new VtRateLimiter(properties.virustotal().perMinute(), properties.virustotal().perDay(), clock);
    }
}
