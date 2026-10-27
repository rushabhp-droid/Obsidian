package com.obsidian.model;

/** SPF / DKIM / DMARC result from the topmost mx.google.com Authentication-Results header. */
public enum AuthCheck {
    PASS, FAIL, NONE
}
