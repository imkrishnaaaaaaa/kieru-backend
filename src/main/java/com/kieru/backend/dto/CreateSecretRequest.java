package com.kieru.backend.dto;

import com.kieru.backend.util.KieruUtil;
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

    private String content;
    private String secretName;
    private String password;

    @Builder.Default
    private int maxViews = 5;

    @Builder.Default
    private int viewTimeSeconds = 300;

    @Builder.Default
    private boolean showTimeBomb = true;

    @Builder.Default
    private KieruUtil.SecretType type = KieruUtil.SecretType.TEXT;

    // FIX: Calculate "Now + 24 Hours" and convert to Milliseconds
    @Builder.Default
    private long expiresAt = Instant.now().plus(24, ChronoUnit.HOURS).toEpochMilli();

    private HttpStatus httpStatus;
}