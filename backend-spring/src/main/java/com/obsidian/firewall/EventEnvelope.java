package com.obsidian.firewall;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/**
 * A message on Redis channel:firewall:events (docs/protocol.md, "Redis").
 * {@code event} is the exact /ws/dashboard frame; orgId and userId route it.
 */
public record EventEnvelope(Kind kind, UUID orgId, UUID userId, JsonNode event) {

    public enum Kind { RULE, SCAN, HEARTBEAT }
}
