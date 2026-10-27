package com.obsidian.auth;

import com.obsidian.model.Account;
import com.obsidian.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password register/login. Web clients get the __Host-session
 * cookie; MOBILE clients get a bearer token. Passkeys use Spring Security's
 * own /webauthn/* and /login/webauthn endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final AccountService accounts;
    private final MobileTokenService mobileTokens;
    private final TurnstileVerifier turnstile;
    private final SecurityContextRepository contextRepository;

    public AuthController(AuthService auth, AccountService accounts, MobileTokenService mobileTokens,
            TurnstileVerifier turnstile, SecurityContextRepository contextRepository) {
        this.auth = auth;
        this.accounts = accounts;
        this.mobileTokens = mobileTokens;
        this.turnstile = turnstile;
        this.contextRepository = contextRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        requireTurnstile(body.turnstileToken());
        User user = auth.register(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(signIn(user, body.client(), request, response));
    }

    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        requireTurnstile(body.turnstileToken());
        User user = auth.authenticate(body.email(), body.password());
        return signIn(user, body.client(), request, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String bearer = BearerTokenFilter.bearerToken(request);
        if (bearer != null) {
            mobileTokens.revoke(bearer);
        } else {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public SessionInfo me(Account account) {
        return accounts.sessionInfo(account);
    }

    private Object signIn(User user, ClientType client, HttpServletRequest request, HttpServletResponse response) {
        if (client == ClientType.MOBILE) {
            return mobileTokens.issue(user);
        }
        UserDetails principal = ObsidianUserDetailsService.principal(user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        if (request.getSession(false) != null) {
            request.changeSessionId(); // no session fixation across the login
        }
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return accounts.sessionInfo(accounts.of(user));
    }

    private void requireTurnstile(String token) {
        if (!turnstile.verify(token)) {
            throw new AuthExceptions.TurnstileFailed();
        }
    }
}
