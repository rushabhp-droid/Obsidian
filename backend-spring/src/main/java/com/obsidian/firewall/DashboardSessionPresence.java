package com.obsidian.firewall;

import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

/**
 * Invalidates a web session after its last dashboard socket closes. The delay
 * lets a page reload or a brief socket reconnect reuse the same login, while
 * connections from other dashboard tabs keep the shared session alive.
 */
@Component
class DashboardSessionPresence {

    private static final long CLOSE_GRACE_SECONDS = 25;

    private final SessionRepository<? extends Session> sessionRepository;
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "dashboard-session-expiry");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Presence> sessions = new HashMap<>();

    DashboardSessionPresence(SessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    synchronized void connected(String sessionId) {
        Presence presence = sessions.computeIfAbsent(sessionId, ignored -> new Presence());
        presence.connections++;
        if (presence.pendingLogout != null) {
            presence.pendingLogout.cancel(false);
            presence.pendingLogout = null;
        }
    }

    synchronized void disconnected(String sessionId) {
        Presence presence = sessions.get(sessionId);
        if (presence == null || presence.connections == 0) return;

        presence.connections--;
        if (presence.connections == 0) {
            presence.pendingLogout = expiry.schedule(
                    () -> logoutIfStillDisconnected(sessionId, presence), CLOSE_GRACE_SECONDS, TimeUnit.SECONDS);
        }
    }

    private synchronized void logoutIfStillDisconnected(String sessionId, Presence expected) {
        if (sessions.get(sessionId) != expected || expected.connections != 0) return;

        // Keep this under the same lock as connected(): a reconnect cannot
        // cancel the timer after we've decided to delete the shared session.
        sessions.remove(sessionId);
        sessionRepository.deleteById(sessionId);
    }

    @PreDestroy
    void stopExpiry() {
        expiry.shutdownNow();
    }

    private static final class Presence {
        private int connections;
        private ScheduledFuture<?> pendingLogout;
    }
}
