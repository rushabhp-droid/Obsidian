package com.obsidian.rules;

/** A report or bypass request that breaks a rule of the API (bad TTL, category). Mapped to 400. */
public class InvalidRuleRequestException extends RuntimeException {

    public InvalidRuleRequestException(String message) {
        super(message);
    }
}
