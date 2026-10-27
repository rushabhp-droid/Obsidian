package com.obsidian.auth;

import com.obsidian.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mobile auth for /api/**: Authorization: Bearer obm_… resolves to the user
 * for this request only (nothing is stored in a session). A bearer header that
 * doesn't resolve is a 401, never a fallback to the cookie session.
 * /ws/firewall's agent tokens are checked by the WebSocket handshake instead.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private final MobileTokenService tokens;

    public BearerTokenFilter(MobileTokenService tokens) {
        this.tokens = tokens;
    }

    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || bearerToken(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<User> user = tokens.resolve(bearerToken(request));
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            return;
        }
        UserDetails principal = ObsidianUserDetailsService.principal(user.get());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
