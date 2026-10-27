package com.obsidian.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password is optional on the web (passkey-only account), required for MOBILE. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @Size(min = 12, max = 128) String password,
        @Size(max = 120) String displayName,
        @Size(max = 120) String organizationName,
        String turnstileToken,
        ClientType client) {}
