package com.obsidian.scanner;

/** /ws/dashboard frame for a new scan verdict: {"type":"SCAN","scan":{…}}. */
public record ScanEvent(String type, ScanLogDto scan) {

    public ScanEvent(ScanLogDto scan) {
        this("SCAN", scan);
    }
}
