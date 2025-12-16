package com.kieru.backend.controller;

import com.kieru.backend.dto.CreateSecretRequest;
import com.kieru.backend.dto.SecretMetadataResponseDTO; // Assuming you kept the DTO name from your previous snippets
import com.kieru.backend.dto.SecretResponseDTO;
import com.kieru.backend.entity.User;
import com.kieru.backend.service.SecretService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/secrets")
@RequiredArgsConstructor
public class SecretController {

    private final SecretService secretService;

    /**
     * 1. CREATE SECRET
     * URL: POST /api/secrets
     * Access: Public (Anonymous) or Authenticated
     */
    @PostMapping("/create")
    public ResponseEntity<SecretMetadataResponseDTO> createSecret(
            @Valid @RequestBody CreateSecretRequest request,
            @AuthenticationPrincipal User user, // Injected by Spring Security (Null if anonymous)
            HttpServletRequest httpRequest // To get IP Address
    ) {
        // 1. Extract Identity
        String ownerId = (user != null) ? user.getId() : null;
        String ipAddress = getClientIp(httpRequest);

        // 2. Call Service
        SecretMetadataResponseDTO response = secretService.createSecret(request, ownerId, ipAddress);
        // 3. Return JSON
        return ResponseEntity.status(response.getHttpStatus()).body(response);
    }

    /**
     * 2. VIEW SECRET CONTENT
     * URL: POST /api/secrets/{id}/access
     * Access: Public (Password Protected via Body)
     * Why POST? Because sending passwords in GET URL params is a security risk (browser history logs it).
     */
    @PostMapping("/{id}/access")
    public ResponseEntity<SecretResponseDTO> getSecretContent(
            @PathVariable String id,
            @RequestBody Map<String, String> body, // Expects {"password": "..."}
            HttpServletRequest httpRequest
    ) {

        Instant accessingTime = Instant.now();
        // 1. Extract Details
        String password = body.get("password");
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // 2. Call Service
        // Note: You need to update your Interface return type if you changed it to SecretResponseDTO
        SecretResponseDTO response = secretService.getSecretContent(id, password, accessingTime, ipAddress, userAgent);

        // 3. Return Logic (Handle HTTP Status based on success/failure)
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            // Logic: If it failed (Expired/Max Views), is it a 404, 410 (Gone), or 403?
            // For now, returning 200 OK with success=false flag is easier for React to handle.
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    // --- HELPER: Get Real IP (Behind Load Balancers/Cloudflare) ---
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}