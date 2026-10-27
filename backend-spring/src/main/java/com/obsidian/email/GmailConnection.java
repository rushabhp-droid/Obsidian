package com.obsidian.email;

import java.time.Instant;

/** GmailConnection in web-dashboard/src/lib/types.ts. */
public record GmailConnection(boolean connected, String address, Instant lastPolledAt) {}
