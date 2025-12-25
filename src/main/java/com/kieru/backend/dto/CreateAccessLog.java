package com.kieru.backend.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CreateAccessLog {
    private String id;

    private Instant accessedAt;

    private String ipAddress;

    private String deviceType;

    private String userAgent;

    private boolean wasSuccessful;

    private String failureReason;
}
