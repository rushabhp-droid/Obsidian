package com.obsidian.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Spring Session (Redis, spring:session:sessions:*) cookie:
 * {@code __Host-session; Secure; HttpOnly; Path=/; SameSite=Lax}, no Domain,
 * so it is bound to this exact host.
 */
@Configuration(proxyBeanMethods = false)
public class SessionConfig {

    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("__Host-session");
        serializer.setCookiePath("/");
        serializer.setUseSecureCookie(true);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
