package com.kieru.backend.dto;

import com.kieru.backend.util.KieruUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSecretRequest {

    // 1. Mandatory Fields
    @NotBlank(message = "Encrypted content is required")
    @JsonProperty("encryptedPayload")
    private String content;

    @NotBlank(message = "Secret name is required")
    private String secretName;
    private String password;

    @Min(value = 1, message = "Max views must be at least 1")
    @Max(value = 20, message = "Max views cannot exceed 20") // Safety cap
    @Builder.Default
    private Integer maxViews = 5;

    @Min(value = 0, message = "Time cannot be negative")
    @Max(value = 600, message = "Max 10mins")
    @Builder.Default
    private Integer viewTimeSeconds = 300;

    @Builder.Default
    private Boolean showTimeBomb = true;

    @NotNull(message = "Secret Type is required")
    @Builder.Default
    private KieruUtil.SecretType type = KieruUtil.SecretType.TEXT;

    @Min(value = 1, message = "Expiry must be a positive timestamp")
    @Builder.Default
    private Long expiresAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();

    private HttpStatus httpStatus;
}