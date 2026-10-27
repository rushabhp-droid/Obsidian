package com.obsidian.config;

import com.obsidian.auth.BearerTokenFilter;
import com.obsidian.auth.InMemoryCreationOptionsRepository;
import com.obsidian.auth.MobileTokenService;
import com.obsidian.auth.PasskeyLoginGuardFilter;
import com.obsidian.auth.TurnstileVerifier;
import java.time.Clock;
import java.util.LinkedHashSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.DefaultLoginPageConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;

/**
 * One filter chain for the whole Spring side of the origin:
 * <ul>
 *   <li>passkeys through Spring Security WebAuthn (its own endpoints and JDBC tables);</li>
 *   <li>password login in AuthController; the Redis session rides the __Host-session cookie (SessionConfig);</li>
 *   <li>mobile bearer tokens on /api/**; agent tokens on /ws/firewall are checked in its handshake;</li>
 *   <li>CSRF via the XSRF-TOKEN cookie, except before sign-in and for bearer requests;</li>
 *   <li>no form login, basic auth, saved requests or generated login/logout pages
 *       (the Next.js app owns /login).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObsidianProperties properties,
            MobileTokenService mobileTokens, TurnstileVerifier turnstile,
            PublicKeyCredentialCreationOptionsRepository creationOptions,
            SecurityContextRepository contextRepository) throws Exception {
        RequestMatcher bearerRequest = request -> BearerTokenFilter.bearerToken(request) != null;

        withoutGeneratedLoginPages(http);
        http
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        // GitHub can't sign in; the controller checks X-Hub-Signature-256 instead.
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/github").permitAll()
                        .requestMatchers("/ws/firewall").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .webAuthn(webAuthn -> webAuthn
                        .rpName(properties.webauthn().rpName())
                        .rpId(properties.webauthn().rpId())
                        .allowedOrigins(new LinkedHashSet<>(properties.browserOrigins()))
                        .disableDefaultRegistrationPage(true)
                        .creationOptionsRepository(creationOptions))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/webhooks/github",
                                "/webauthn/authenticate/options", "/login/webauthn")
                        .ignoringRequestMatchers(bearerRequest))
                .addFilterAfter(new PasskeyLoginGuardFilter(turnstile), SecurityContextHolderFilter.class)
                .addFilterBefore(new BearerTokenFilter(mobileTokens), AnonymousAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    UserCredentialRepository userCredentialRepository(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    @Bean
    PublicKeyCredentialCreationOptionsRepository creationOptionsRepository(Clock clock) {
        return new InMemoryCreationOptionsRepository(clock);
    }

    /** Spring's generated /login and /logout pages would shadow the Next.js /login route. */
    @SuppressWarnings("unchecked") // the configurer's class literal is a raw type
    private static void withoutGeneratedLoginPages(HttpSecurity http) {
        http.removeConfigurer(DefaultLoginPageConfigurer.class);
    }

    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax").path("/"));
        return repository;
    }
}
