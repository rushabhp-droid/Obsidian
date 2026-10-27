package com.obsidian.rules;

/** A rule or scan value that doesn't parse as its indicator type. Mapped to 400. */
public class InvalidIndicatorException extends RuntimeException {

    public InvalidIndicatorException(String message) {
        super(message);
    }
}
