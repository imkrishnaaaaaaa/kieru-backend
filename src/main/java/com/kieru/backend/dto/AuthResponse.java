package com.kieru.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String userId;
    private String email;
    private String displayName;
    private String role;           // "USER", "ADMIN"

    // CRITICAL for Single Session:
    // The frontend must save this and send it in the header 'X-Session-Version'
    private String sessionVersion;
}