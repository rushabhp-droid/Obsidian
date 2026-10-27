package com.obsidian.firewall;

import com.obsidian.auth.AccountService;
import com.obsidian.model.Account;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** /ws/dashboard handshake: the session cookie (already checked by Spring Security) becomes the socket's Account. */
class DashboardHandshakeInterceptor implements HandshakeInterceptor {

    static final String ACCOUNT = "obsidian.account";
    static final String SESSION_ID = "obsidian.sessionId";

    private final AccountService accounts;

    DashboardHandshakeInterceptor(AccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Map<String, Object> attributes) {
        Optional<Account> account = accounts.find(SecurityContextHolder.getContext().getAuthentication());
        if (account.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        HttpSession session = servletRequest.getServletRequest().getSession(false);
        if (session == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ACCOUNT, account.get());
        attributes.put(SESSION_ID, session.getId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Exception exception) {}
}
