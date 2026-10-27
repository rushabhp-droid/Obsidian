package com.obsidian.codeguard;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code obsidian.codeguard.*}: the GitHub App and the scanners. Off until the app is configured. */
@ConfigurationProperties("obsidian.codeguard")
public record CodeGuardProperties(
        String appId,
        String privateKeyPath,
        String webhookSecret,
        /** Admin of the organization that owns this GitHub App's findings (one App per organization). */
        String orgAdminEmail,
        String apiUrl,
        String gitleaks,
        String osvScanner,
        Duration toolTimeout,
        long maxArchiveBytes,
        long maxAssetBytes) {

    public boolean enabled() {
        return notBlank(appId) && notBlank(privateKeyPath) && notBlank(webhookSecret) && notBlank(orgAdminEmail);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
