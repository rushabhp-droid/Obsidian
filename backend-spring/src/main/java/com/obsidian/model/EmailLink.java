package com.obsidian.model;

/** One link in a scanned email; also its JSON shape in scanned_emails.links and the API. */
public record EmailLink(String url, Verdict verdict, int vtDetections) {}
