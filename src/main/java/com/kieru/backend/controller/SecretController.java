package com.kieru.backend.controller;

import com.kieru.backend.annotation.RateLimit;
import com.kieru.backend.annotation.RateLimitType;
import com.kieru.backend.dto.CreateSecretRequest;
import com.kieru.backend.dto.SecretMetadataResponseDTO;
import com.kieru.backend.dto.SecretResponseDTO;
import com.kieru.backend.entity.User;
import com.kieru.backend.service.SecretService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/secrets")
@RequiredArgsConstructor
@Slf4j
public class SecretController {

    private final SecretService secretService;

    @PostMapping("/create")
    @RateLimit(type = RateLimitType.USER, requests = 30, windowSeconds = 3600, lockDurationMinutes = 15)
    public ResponseEntity<SecretMetadataResponseDTO> createSecret(
            @Valid @RequestBody CreateSecretRequest request,
            @AuthenticationPrincipal User user,
            HttpServletRequest httpRequest
    ) {
        String ownerId = (user != null) ? user.getId() : null;
        String ipAddress = getClientIp(httpRequest);

        log.info("SecretSecretController :: Controller : Controller : Create secret request from IP: {}, Owner: {}", ipAddress, ownerId);

        SecretMetadataResponseDTO response = secretService.createSecret(request, ownerId, ipAddress);

        log.info("SecretController :: Controller : Create secret response - Success: {}, SecretId: {}", response.getIsSuccess(), response.getSecretId());

        return ResponseEntity.status(response.getHttpStatus()).body(response);
    }

    @PostMapping("/{id}/access")
    @RateLimit(type = RateLimitType.IP, requests = 50, windowSeconds = 3600, lockDurationMinutes = 10)
    public ResponseEntity<SecretResponseDTO> getSecretContent(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest httpRequest
    ) {
        Instant accessingTime = Instant.now();
        String password = (body != null) ? body.get("password") : null;
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("SecretController :: Controller : Access secret request. SecretId: {}, IP: {}", id, ipAddress);

        SecretResponseDTO response = secretService.getSecretContent(id, password, accessingTime, ipAddress, userAgent);

        HttpStatus status = response.getHttpStatus() != null ? response.getHttpStatus() : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/validation")
    @RateLimit(type = RateLimitType.USER, requests = 75, windowSeconds = 3600, lockDurationMinutes = 10)
    public ResponseEntity<SecretMetadataResponseDTO> validateSecret(
            @RequestParam(name = "id") String secretId
    ) {
        log.info("SecretController :: Controller : Validate secret request. SecretId: {}", secretId);

        SecretMetadataResponseDTO response = secretService.validateSecret(secretId);

        HttpStatus status = response.getHttpStatus() != null ? response.getHttpStatus() : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/update-password/{id}")
    @RateLimit(type = RateLimitType.USER, requests = 5, windowSeconds = 600, lockDurationMinutes = 5)
    public ResponseEntity<SecretResponseDTO> updateSecretPassword(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            log.warn("SecretController :: Controller : Unauthorized password update attempt for secret: {}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String password = body.get("password");

        if (password == null || password.isBlank()) {
            log.warn("SecretController :: Controller : Empty password provided for secret: {}", id);
            return ResponseEntity.badRequest().body(
                    SecretResponseDTO.builder()
                            .isSuccess(false)
                            .message("Password is required")
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build()
            );
        }

        log.info("SecretController :: Controller : Update password request for secret: {} by user: {}", id, user.getId());

        SecretResponseDTO response = secretService.updateSecretPassword(id, password, user.getId());

        HttpStatus status = response.getHttpStatus() != null ? response.getHttpStatus() : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    private String getClientIp(HttpServletRequest request) {
        // Cloudflare
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp;
        }

        // Other proxies/load balancers
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}