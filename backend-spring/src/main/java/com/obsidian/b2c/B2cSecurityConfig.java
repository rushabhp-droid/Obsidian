package com.obsidian.b2c;

import com.obsidian.auth.BearerTokenFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The B2C app's own filter chain, ahead of the dashboard's: only /api/b2c/**,
 * stateless, bearer tokens (obc_) only, no cookies and therefore no CSRF.
 * A dashboard token (obm_) or session cookie never authenticates here, and an
 * obc_ token never reaches the dashboard chain's /api/v1.
 */
@Configuration(proxyBeanMethods = false)
public class B2cSecurityConfig implements WebMvcConfigurer {

    static final String AUTHORITY = "B2C_CONSUMER";

    @Bean
    @Order(1)
    SecurityFilterChain b2cFilterChain(HttpSecurity http, ConsumerTokenService tokens) throws Exception {
        http
                .securityMatcher("/api/b2c/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/b2c/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/b2c/v1/status").permitAll()
                        .anyRequest().hasAuthority(AUTHORITY))
                .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new ConsumerTokenFilter(tokens), AnonymousAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ConsumerArgumentResolver());
    }

    /** Authorization: Bearer obc_… → the consumer, for this request only. Anything else that looks like a token is a 401. */
    static class ConsumerTokenFilter extends OncePerRequestFilter {

        private final ConsumerTokenService tokens;

        ConsumerTokenFilter(ConsumerTokenService tokens) {
            this.tokens = tokens;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String bearer = BearerTokenFilter.bearerToken(request);
            if (bearer == null) {
                chain.doFilter(request, response);
                return;
            }
            Optional<Consumer> consumer = tokens.resolve(bearer);
            if (consumer.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(consumer.get(), null,
                    List.of(new SimpleGrantedAuthority(AUTHORITY))));
            SecurityContextHolder.setContext(context);
            chain.doFilter(request, response);
        }
    }

    /** Lets B2C controllers take a {@link Consumer} parameter for the signed-in consumer. */
    static class ConsumerArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return Consumer.class.equals(parameter.getParameterType());
        }

        @Override
        public Consumer resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Consumer consumer) {
                return consumer;
            }
            throw new AuthenticationCredentialsNotFoundException("Sign in first.");
        }
    }
}
