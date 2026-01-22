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

    private Integer viewTimeSeconds;
    private Boolean showTimeBomb;

    private Instant expiresAt;
    private Integer viewsLeft;

    private Boolean isExpired;
    private Boolean isDeleted;
    private Boolean isActive;
    private Boolean isSuccess;
    private Boolean isValidationPassed;
    private String message;

    private HttpStatus httpStatus;
}