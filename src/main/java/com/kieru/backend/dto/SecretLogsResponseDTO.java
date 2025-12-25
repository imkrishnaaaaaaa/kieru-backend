package com.kieru.backend.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SecretLogsResponseDTO {
    private String secretId;

    // Nested List of Log Entries
    private List<LogEntry> logs;

    private int totalCount;
    private boolean isSuccess;

    private String failureReason;

    private String message;

    private HttpStatus httpStatus;


    @Data
    @Builder
    public static class LogEntry {
        private String ipAddress;      // We might mask this (e.g., "192.168.x.x") for privacy
        private String deviceType;     // "Mobile", "Desktop"
        private String userAgent;      // Raw browser string
        private Instant accessedAt;
        private boolean wasSuccessful; // True = Viewed, False = Wrong Password
    }

}