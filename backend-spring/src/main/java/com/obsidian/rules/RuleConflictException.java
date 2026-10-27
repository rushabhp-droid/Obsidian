package com.obsidian.rules;

/** An administrator policy already decides this indicator. Mapped to 409. */
public class RuleConflictException extends RuntimeException {

    public RuleConflictException(String message) {
        super(message);
    }
}
