package com.kieru.backend.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@Data
@Builder
public class SecretMetadataResponseDTO {
    // 1. Identification
    private String secretId;
    private String secretName;        // e.g., "Wifi Password" (User defined)

    // 2. Status Flags
    private Boolean isActive;         // Derived from (Now < Expire AND Views < Limit)
    private Boolean isSuccess;
    private Boolean showTimeBomb;     // <--- ADDED: Syncs with dashboard UI
    private Integer viewTimeInSeconds;
    private Boolean isPasswordProtected;

    // 3. Stats
    private Integer maxViews;
    private Integer currentViews;         // How many people have seen it so far
    private Integer viewsLeft;

    // 4. Timestamps
    private Boolean isDeleted;
    private Instant expiresAt;           // Epoch Millis
    private Instant createdAt;  // For sorting the list

    private HttpStatus httpStatus;
    private String message;
}