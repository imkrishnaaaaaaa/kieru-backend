package com.kieru.backend.controller;

import com.kieru.backend.dto.SecretLogsResponseDTO;
import com.kieru.backend.dto.SecretMetadataResponseDTO;
import com.kieru.backend.entity.User;
import com.kieru.backend.service.SecretService; // Updated package name to match standard singular 'service'
import lombok.RequiredArgsConstructor;
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
public class DashboardController {

    private final SecretService secretService;

    /**
     * 1. LIST MY SECRETS
     * Returns ONLY Metadata (Name, Status, Views Left).
     * Usage: GET /api/dashboard/secrets?start=0&limit=10&onlyActive=true
     */
    @GetMapping("/secrets")
    public ResponseEntity<List<SecretMetadataResponseDTO>> getMySecrets(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "start", defaultValue = "0") int startOffset,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "isActive", defaultValue = "true") boolean onlyActive
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<SecretMetadataResponseDTO> secretsList = secretService.getMySecretsMeta(
                user.getId(),
                startOffset,
                limit,
                onlyActive
        );

        return ResponseEntity.ok(secretsList);
    }

    /**
     * 2. VIEW HISTORY LOGS
     * Returns IP addresses, times, and success/fail status for ONE secret.
     * Usage: GET /api/dashboard/secrets/{id}/50/logs
     */
    @GetMapping("/secrets/{id}/{limit}/logs")
    public ResponseEntity<SecretLogsResponseDTO> getSecretLogs(
            @PathVariable("id") String secretId,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Create Pageable object from limit
        Pageable pageable = PageRequest.ofSize(limit);

        // Call service (Aligned with your Interface definition)
        // Note: The Service should internally verify if 'user' owns 'secretId'
        // since we aren't passing the user ID here based on your interface.
        SecretLogsResponseDTO logs = secretService.getSecretLogs(secretId, pageable);

        return ResponseEntity.ok(logs);
    }

    /**
     * 3. KILL SWITCH
     * Allows the owner to delete the secret immediately.
     */
    @DeleteMapping("/secrets/{id}")
    public ResponseEntity<String> deleteSecret(
            @PathVariable String id,
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Aligned with your Interface definition
        secretService.deleteSecret(id);

        return ResponseEntity.ok("Secret deleted successfully");
    }
}