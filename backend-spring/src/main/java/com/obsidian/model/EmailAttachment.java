package com.obsidian.model;

/** One attachment in a scanned email; hashed in memory, the bytes are never stored. */
public record EmailAttachment(String filename, String sha256, Verdict verdict, int vtDetections) {}
