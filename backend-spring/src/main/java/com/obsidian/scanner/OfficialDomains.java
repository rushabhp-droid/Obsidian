package com.obsidian.scanner;

import com.obsidian.config.ObsidianProperties;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** The verified root domains of obsidian.email.official-domains; a subdomain of one counts. */
@Component
public class OfficialDomains {

    private final List<String> domains;

    public OfficialDomains(ObsidianProperties properties) {
        this.domains = properties.email() == null || properties.email().officialDomains() == null
                ? List.of()
                : properties.email().officialDomains().stream().map(d -> d.trim().toLowerCase(Locale.ROOT)).toList();
    }

    public boolean contains(String domain) {
        if (domain == null) {
            return false;
        }
        String host = domain.toLowerCase(Locale.ROOT);
        return domains.stream().anyMatch(root -> host.equals(root) || host.endsWith("." + root));
    }
}
