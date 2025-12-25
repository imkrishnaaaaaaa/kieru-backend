package com.kieru.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import org.springframework.http.HttpStatus;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecretResponseDTO {
    private String id;
    private String content;
    private String type;

    private int viewTimeSeconds;

    private boolean showTimeBomb;

    private Instant expiresAt;
    private int viewsLeft;

    @Builder.Default
    private boolean isExpired = false;

    @Builder.Default
    private boolean isDeleted = false;

    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private boolean isSuccess;

    @Builder.Default
    private boolean isValidationPassed = true;
    private String message;

    private HttpStatus httpStatus;
}