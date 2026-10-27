package com.obsidian.b2c;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Domains a consumer report never blocks globally: the bundled top-domain list
 * (resources/b2c/protected-domains.txt) plus admin additions in protected_domains.
 * A subdomain is protected with its parent, except under shared hosting
 * (resources/b2c/shared-hosting-suffixes.txt), where anyone can get a name.
 */
@Component
public class ProtectedDomains {

    private final JdbcTemplate jdbc;
    private final Set<String> bundled;
    private final Set<String> sharedHosting;

    public ProtectedDomains(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.bundled = read("b2c/protected-domains.txt");
        this.sharedHosting = read("b2c/shared-hosting-suffixes.txt");
    }

    /** {@code domain} must be normalized (Indicators.normalize). */
    public boolean isProtected(String domain) {
        List<String> candidates = ConsumerScanService.domainAndParents(domain);
        for (String candidate : candidates) {
            if (!candidate.equals(domain) && sharedHosting.contains(candidate)) {
                return false; // e.g. someone.github.io-style names under a protected domain
            }
            if (bundled.contains(candidate)) {
                return true;
            }
        }
        return !jdbc.queryForList("select domain from protected_domains where domain in ("
                + String.join(",", candidates.stream().map(c -> "?").toList()) + ")", String.class,
                candidates.toArray()).isEmpty();
    }

    public List<String> adminAdded() {
        return jdbc.queryForList("select domain from protected_domains order by domain", String.class);
    }

    public void add(String domain, UUID addedBy) {
        jdbc.update("insert into protected_domains (domain, added_by) values (?, ?) on conflict (domain) do nothing",
                domain, addedBy);
    }

    public void remove(String domain) {
        jdbc.update("delete from protected_domains where domain = ?", domain);
    }

    private static Set<String> read(String resource) {
        Set<String> domains = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(domains::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + resource, e);
        }
        return Set.copyOf(domains);
    }
}
