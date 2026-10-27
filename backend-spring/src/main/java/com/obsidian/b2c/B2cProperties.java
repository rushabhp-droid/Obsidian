package com.obsidian.b2c;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code obsidian.b2c.*}: the Android consumer app's API. */
@ConfigurationProperties("obsidian.b2c")
public record B2cProperties(
        String googleClientId,
        boolean logCodes,
        Duration codeTtl,
        Duration resendAfter,
        int reportersToBlock,
        Duration reportWindow,
        Mail mail) {

    public record Mail(String host, int port, String username, String password, String from) {
        public boolean configured() {
            return host != null && !host.isBlank();
        }
    }

    public boolean googleConfigured() {
        return googleClientId != null && !googleClientId.isBlank();
    }
}
