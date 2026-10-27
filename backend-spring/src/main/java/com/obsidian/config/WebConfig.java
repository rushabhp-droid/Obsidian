package com.obsidian.config;

import com.obsidian.auth.AccountService;
import com.obsidian.auth.CurrentAccountArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final AccountService accounts;

    public WebConfig(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentAccountArgumentResolver(accounts));
    }
}
