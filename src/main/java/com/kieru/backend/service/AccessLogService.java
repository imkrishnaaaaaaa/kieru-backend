package com.kieru.backend.service;

import com.kieru.backend.dto.CreateAccessLog;
import com.kieru.backend.entity.SecretAccessLog;
import com.kieru.backend.entity.SecretMetadata;
import com.kieru.backend.repository.AccessLogRepository;
import com.kieru.backend.repository.SecretMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository logRepo;
    private final SecretMetadataRepository metaRepo;

    // 1. ASYNC LOGGING (Uses the Thread Pool)
    @Async("taskExecutor")
    @Transactional
    public void saveLogAsync(CreateAccessLog logDto, String secretId) {
        try {
            // Use getReferenceById to avoid a DB Select query (just links the ID)
            SecretMetadata ref = metaRepo.getReferenceById(secretId);

            SecretAccessLog log = new SecretAccessLog();
            log.setSecret(ref);
            log.setIpAddress(logDto.getIpAddress());
            log.setUserAgent(logDto.getUserAgent());
            log.setAccessedAt(logDto.getAccessedAt());
            log.setWasSuccessful(logDto.isWasSuccessful());
            log.setFailureReason(logDto.getFailureReason());

            logRepo.save(log);
        } catch (Exception e) {
            System.err.println("Async Log Failed: " + e.getMessage());
        }
    }

    // 2. ASYNC STATE UPDATE (Fire and Forget DB updates)
    @Async("taskExecutor")
    @Transactional
    public void updateSecretStateAsync(String secretId, int newViewsLeft, boolean disableSecret) {
        // Atomic decrement is handled in service logic, this persists the result
        // Ideally we use a custom query here for safety, but saving the entity works for Phase 1
        SecretMetadata meta = metaRepo.findById(secretId).orElse(null);
        if (meta != null) {
            meta.setViewsLeft(newViewsLeft);
            if (disableSecret) {
                meta.setActive(false);
            }
            metaRepo.save(meta);
        }
    }

    // 3. ASYNC DISABLE ONLY (For Expiry/Delete cases)
    @Async("taskExecutor")
    @Transactional
    public void disableSecretAsync(String secretId) {
        SecretMetadata meta = metaRepo.findById(secretId).orElse(null);
        if (meta != null) {
            meta.setActive(false);
            metaRepo.save(meta);
        }
    }
}