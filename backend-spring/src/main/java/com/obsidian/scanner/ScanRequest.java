package com.obsidian.scanner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.obsidian.model.Channel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * POST /api/v1/scan/unified. Saved contacts send URLs only;
 * mobile never sends file hashes. Limits are checked again in ScanService.
 */
public record ScanRequest(
        @NotNull Channel channel,
        @Size(max = 255) String sender,
        @JsonProperty("isSavedContact") boolean isSavedContact,
        @Size(max = 5000) String text,
        @Size(max = 20) List<String> urls,
        @Size(max = 20) List<String> fileHashes) {}
