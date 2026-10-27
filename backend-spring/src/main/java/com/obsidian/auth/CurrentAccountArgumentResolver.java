package com.obsidian.auth;

import com.obsidian.model.Account;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controllers take an {@link Account} parameter for the signed-in user (401 otherwise). */
public class CurrentAccountArgumentResolver implements HandlerMethodArgumentResolver {

    private final AccountService accounts;

    public CurrentAccountArgumentResolver(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Account.class.equals(parameter.getParameterType());
    }

    @Override
    public Account resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return accounts.require(SecurityContextHolder.getContext().getAuthentication());
    }
}
