package com.obsidian.firewall;

import com.obsidian.model.Account;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * /ws/dashboard: DashboardEvent frames (types.ts). Organization-wide events reach
 * everyone in the organization; a user's own events reach that user and the
 * organization's admins.
 */
@Component
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final DashboardSessionPresence presence;

    public DashboardWebSocketHandler(DashboardSessionPresence presence) {
        this.presence = presence;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 512 * 1024));
        String sessionId = sessionId(session);
        if (sessionId != null) presence.connected(sessionId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeConnection(session);
    }

    void deliver(EventEnvelope envelope) {
        TextMessage message = new TextMessage(envelope.event().toString());
        for (WebSocketSession session : sessions.values()) {
            Account account = (Account) session.getAttributes().get(DashboardHandshakeInterceptor.ACCOUNT);
            boolean adminOnly = envelope.kind() == EventEnvelope.Kind.CODE; // Code Guard findings are SOC-only
            if (account != null && sees(account, envelope.orgId(), envelope.userId())
                    && (!adminOnly || account.isAdmin())) {
                try {
                    session.sendMessage(message);
                } catch (IOException | IllegalStateException e) {
                    try {
                        session.close(CloseStatus.SERVER_ERROR);
                    } catch (IOException ignored) {
                        // Remove the failed connection below even if the transport is already gone.
                    }
                    removeConnection(session);
                }
            }
        }
    }

    static boolean sees(Account account, UUID orgId, UUID userId) {
        if (orgId == null) {
            return userId != null && userId.equals(account.userId());
        }
        if (!orgId.equals(account.orgId())) {
            return false;
        }
        return userId == null || userId.equals(account.userId()) || account.isAdmin();
    }

    private static String sessionId(WebSocketSession session) {
        Object sessionId = session.getAttributes().get(DashboardHandshakeInterceptor.SESSION_ID);
        return sessionId instanceof String value ? value : null;
    }

    private void removeConnection(WebSocketSession session) {
        if (sessions.remove(session.getId()) == null) return;
        String sessionId = sessionId(session);
        if (sessionId != null) presence.disconnected(sessionId);
    }
}
