package com.kieru.backend.service;

import com.kieru.backend.dto.CreateSecretRequest;
import com.kieru.backend.dto.SecretLogsResponseDTO;
import com.kieru.backend.dto.SecretMetadataResponseDTO;
import com.kieru.backend.dto.SecretResponseDTO;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface SecretService {

    SecretMetadataResponseDTO createSecret(CreateSecretRequest request, String ownerId, String ipAddress);

    SecretMetadataResponseDTO validateSecret(String secretId);

    SecretResponseDTO getSecretContent(String id, String password, Instant accessedAt, String ipAddress, String userAgent);

    SecretMetadataResponseDTO deleteSecret(String secretId);

    List<SecretMetadataResponseDTO> getMySecretsMeta(String ownerId, int startOffset, int limit, boolean onlyActive);

    SecretLogsResponseDTO getSecretLogs(String secretId, Pageable pageable);

    SecretResponseDTO updateSecretPassword(String secretId, String newPassword);
}