package com.obsidian.firewall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** A real WebSocket client that records every frame it receives. */
final class SocketClient extends TextWebSocketHandler implements AutoCloseable {

    final List<JsonNode> frames = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private WebSocketSession session;

    static SocketClient connect(String url, WebSocketHttpHeaders headers) throws Exception {
        SocketClient client = new SocketClient();
        client.session = new StandardWebSocketClient().execute(client, headers, URI.create(url)).get();
        return client;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        frames.add(mapper.readTree(message.getPayload()));
    }

    JsonNode await(Predicate<JsonNode> matching) {
        return Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> frames.stream().filter(matching).findFirst().orElse(null), java.util.Objects::nonNull);
    }

    void send(String json) throws Exception {
        session.sendMessage(new TextMessage(json));
    }

    @Override
    public void close() throws Exception {
        if (session != null && session.isOpen()) {
            session.close();
        }
    }
}
