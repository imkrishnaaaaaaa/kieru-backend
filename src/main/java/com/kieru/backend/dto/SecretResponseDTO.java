package com.kieru.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import org.springframework.http.HttpStatus;

@Data
@Builder
public class SecretResponseDTO {
    private String id;
    private String content;
    private String type;

    private int viewTimeSeconds;
    private boolean showTimeBomb;

    private Instant expiresAt;
    private int viewsLeft;

    private boolean isExpired;
    private boolean isDeleted;
    private boolean isActive;
    private boolean isSuccess;
    private boolean isValidationPassed;
    private String message;

    private HttpStatus httpStatus;
}