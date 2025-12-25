package com.kieru.backend.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
public class SecretMetadataResponseDTO {
    // 1. Identification
    private String secretId;
    private String secretName;        // e.g., "Wifi Password" (User defined)

    // 2. Status Flags
    private boolean isActive;         // Derived from (Now < Expire AND Views < Limit)
    private boolean isSuccess;
    private boolean showTimeBomb;     // <--- ADDED: Syncs with dashboard UI
    private int viewTimeInSeconds;         // How many people have seen it so far

    // 3. Stats
    private int maxViews;
    private int currentViews;         // How many people have seen it so far

    // 4. Timestamps
    private boolean isDeleted;
    private Instant expiresAt;           // Epoch Millis
    private Instant createdAt;  // For sorting the list

    private HttpStatus httpStatus;
    private String message;
}