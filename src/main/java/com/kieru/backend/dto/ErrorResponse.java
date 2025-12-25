package com.kieru.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;       // e.g. 400, 404, 500
    private String error;     // e.g. "Bad Request"
    private String message;   // e.g. "Daily limit exceeded"
    private String path;      // e.g. "/api/secrets"
}