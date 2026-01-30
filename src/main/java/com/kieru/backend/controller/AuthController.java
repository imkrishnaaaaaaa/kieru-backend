package com.kieru.backend.controller;

import com.kieru.backend.annotation.RateLimit;
import com.kieru.backend.annotation.RateLimitType;
import com.kieru.backend.dto.AuthResponse;
import com.kieru.backend.dto.LoginRequest;
import com.kieru.backend.entity.User;

import com.kieru.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 1. LOGIN
     * Public Endpoint. Accepts Firebase Token.
     * Returns Session Version.
     */
    @PostMapping("/login")
    @RateLimit(type = RateLimitType.IP, requests = 100, windowSeconds = 300, lockDurationMinutes = 5)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        AuthResponse response = authService.login(request, ip);

        return ResponseEntity.ok(response);
    }

    /**
     * 2. LOGOUT
     * Authenticated Endpoint.
     * Uses @AuthenticationPrincipal to get the current user ID securely.
     */
    @PostMapping("/logout")
    @RateLimit(type = RateLimitType.IP, requests = 50, windowSeconds = 300, lockDurationMinutes = 5)
    public ResponseEntity<String> logout(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.badRequest().body("Not logged in");
        }

        authService.logout(user.getId());
        return ResponseEntity.ok("Logged out successfully");
    }
}