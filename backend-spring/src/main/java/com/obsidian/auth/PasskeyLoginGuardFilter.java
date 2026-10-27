package com.obsidian.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Wraps Spring Security's passkey login:
 * <ul>
 *   <li>POST /webauthn/authenticate/options rotates an existing session id.
 *       Spring's WebAuthn filter has no session strategy, so without this the
 *       pre-login session id would survive the login.</li>
 *   <li>POST /login/webauthn needs a valid CF-Turnstile-Response header.
 *       The options call isn't checked: the dashboard sends the same
 *       single-use token to both.</li>
 * </ul>
 */
public class PasskeyLoginGuardFilter extends OncePerRequestFilter {

    static final String TURNSTILE_HEADER = "CF-Turnstile-Response";
    private static final RequestMatcher OPTIONS =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/webauthn/authenticate/options");
    private static final RequestMatcher LOGIN =
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login/webauthn");

    private final TurnstileVerifier turnstile;

    public PasskeyLoginGuardFilter(TurnstileVerifier turnstile) {
        this.turnstile = turnstile;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !OPTIONS.matches(request) && !LOGIN.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (OPTIONS.matches(request)) {
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }
        } else if (!turnstile.verify(request.getHeader(TURNSTILE_HEADER))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getOutputStream().write(("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,"
                    + "\"detail\":\"" + new AuthExceptions.TurnstileFailed().getMessage() + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
