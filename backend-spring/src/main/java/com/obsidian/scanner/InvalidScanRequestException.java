package com.obsidian.scanner;

/** A scan request that breaks the contract in docs/protocol.md. Mapped to 400. */
public class InvalidScanRequestException extends RuntimeException {

    public InvalidScanRequestException(String message) {
        super(message);
    }
}
