package com.obsidian.auth;

/** Errors of the auth API, mapped to problem details by ApiExceptionHandler. */
public final class AuthExceptions {

    private AuthExceptions() {}

    /** 401. Same message for an unknown email and a wrong password. */
    public static class InvalidCredentials extends RuntimeException {
        public InvalidCredentials() {
            super("The email or password is incorrect.");
        }
    }

    /** 400. Turnstile rejected or missing the token. */
    public static class TurnstileFailed extends RuntimeException {
        public TurnstileFailed() {
            super("The bot check failed. Complete the challenge and try again.");
        }
    }

    /** 409. */
    public static class AccountExists extends RuntimeException {
        public AccountExists() {
            super("An account with this email already exists.");
        }
    }

    /** 400. */
    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) {
            super(message);
        }
    }
}
