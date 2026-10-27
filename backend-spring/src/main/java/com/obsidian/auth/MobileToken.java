package com.obsidian.auth;

import java.time.Instant;

/** What a mobile login returns instead of a session cookie. */
public record MobileToken(String token, Instant expiresAt) {}
