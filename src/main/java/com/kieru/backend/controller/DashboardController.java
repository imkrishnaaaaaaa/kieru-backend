package com.kieru.backend.controller;

import com.kieru.backend.annotation.RateLimit;
import com.kieru.backend.annotation.RateLimitType;
import com.kieru.backend.dto.SecretLogsResponseDTO;
import com.kieru.backend.dto.SecretMetadataResponseDTO;
import com.kieru.backend.entity.User;
import com.kieru.backend.service.SecretService; // Updated package name to match standard singular 'service'
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final SecretService secretService;

    /**
     * 1. LIST MY SECRETS
     * Returns ONLY Metadata (Name, Status, Views Left).
     * Usage: GET /api/dashboard/secrets?start=0&limit=10&onlyActive=true
     */
    @GetMapping("/secrets")
    @RateLimit(type = RateLimitType.USER, requests = 30, windowSeconds = 300, lockDurationMinutes = 5)
    public ResponseEntity<List<SecretMetadataResponseDTO>> getMySecrets(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "page", defaultValue = "0") int pageNumber,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "onlyActive", defaultValue = "false") boolean onlyActive
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("DashboardController :: Controller : Getting ({}) created secrets", limit);
        List<SecretMetadataResponseDTO> secretsList = secretService.getMySecretsMeta(
                user.getId(),
                pageNumber,
                limit,
                onlyActive
        );
        log.info("DashboardController :: Controller : got ({}) created secrets", secretsList.size());

        return ResponseEntity.ok(secretsList);
    }

    /**
     * 2. VIEW HISTORY LOGS
     * Returns IP addresses, times, and success/fail status for ONE secret.
     * Usage: GET /api/dashboard/secrets/{id}/50/logs
     */
    @GetMapping("/secrets/{id}/{limit}/logs")
    @RateLimit(type = RateLimitType.USER, requests = 15, windowSeconds = 300, lockDurationMinutes = 5)
    public ResponseEntity<SecretLogsResponseDTO> getSecretLogs(
            @PathVariable("id") String secretId,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Pageable pageable = PageRequest.ofSize(limit);
        log.info("DashboardController :: Controller : Getting latest ({}) logs of secret: {}", limit, secretId);
        SecretLogsResponseDTO logs = secretService.getSecretLogs(secretId, pageable);
        log.info("DashboardController :: Controller : Got ({}) logs of secret{}", logs.getLogs().size(), secretId);

        return ResponseEntity.ok(logs);
    }

    /**
     * 3. KILL SWITCH
     * Allows the owner to delete the secret immediately.
     */
    @DeleteMapping("/secrets/delete/{id}")
    @RateLimit(type = RateLimitType.USER, requests = 15, windowSeconds = 300, lockDurationMinutes = 5)
    public ResponseEntity<String> deleteSecret(
            @PathVariable String id,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("DashboardController :: Controller : Attempt to delete the secret: {}", id);
        secretService.deleteSecret(id);

        return ResponseEntity.ok("Secret deleted successfully");
    }
}