package com.obsidian.config;

/** The signed-in user may not do this (e.g. a non-admin registering an agent). Mapped to 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
