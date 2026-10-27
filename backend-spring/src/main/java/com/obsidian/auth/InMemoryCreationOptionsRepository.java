package com.obsidian.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;

/**
 * Passkey registration options keyed by session id, in memory. Spring's
 * default repository puts them in the HttpSession, but in Spring Security
 * 6.5 PublicKeyCredentialCreationOptions isn't Serializable, so the
 * Redis-backed session fails to save. Single instance only; entries live
 * five minutes.
 */
public class InMemoryCreationOptionsRepository implements PublicKeyCredentialCreationOptionsRepository {

    private static final Duration TTL = Duration.ofMinutes(5);

    private record Entry(PublicKeyCredentialCreationOptions options, Instant expiresAt) {}

    private final Map<String, Entry> bySession = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryCreationOptionsRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(HttpServletRequest request, HttpServletResponse response,
            PublicKeyCredentialCreationOptions options) {
        Instant now = clock.instant();
        bySession.values().removeIf(entry -> entry.expiresAt().isBefore(now));
        String sessionId = request.getSession().getId();
        if (options == null) {
            bySession.remove(sessionId);
        } else {
            bySession.put(sessionId, new Entry(options, now.plus(TTL)));
        }
    }

    @Override
    public PublicKeyCredentialCreationOptions load(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Entry entry = session == null ? null : bySession.get(session.getId());
        return entry == null || entry.expiresAt().isBefore(clock.instant()) ? null : entry.options();
    }
}
