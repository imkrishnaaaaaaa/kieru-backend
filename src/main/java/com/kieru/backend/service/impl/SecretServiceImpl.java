package com.kieru.backend.service.impl;

import com.kieru.backend.dto.*;
import com.kieru.backend.entity.SecretAccessLog;
import com.kieru.backend.entity.SecretMetadata;
import com.kieru.backend.entity.SecretPayload;
import com.kieru.backend.repository.AccessLogRepository;
import com.kieru.backend.repository.SecretMetadataRepository;
import com.kieru.backend.repository.SecretPayloadRepository;
import com.kieru.backend.repository.UserRepository;
import com.kieru.backend.service.AccessLogService;
import com.kieru.backend.service.SecretService;
import com.kieru.backend.util.KieruUtil;
import com.kieru.backend.util.RedisKeyUtil;
import com.kieru.backend.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SecretServiceImpl implements SecretService {

    private final SecretPayloadRepository payloadRepo;
    private final SecretMetadataRepository metaRepo;
    private final AccessLogRepository logRepo;
    private final UserRepository userRepo;
    private final StringRedisTemplate redisTemplate;
    private final SecurityUtil securityUtil;

    private final KieruUtil kieruUtil;
    private final RedisKeyUtil redisKeyUtil;
    private final AccessLogService accessLogService;

    @Override
    @Transactional
    public SecretMetadataResponseDTO createSecret(CreateSecretRequest request, String ownerId, String ipAddress) {

        String userPlan;
        String redisOwnerSubscriptionKey = redisKeyUtil.buildKey(RedisKeyUtil.KeyType.SUBSCRIPTION_PLAN, ownerId != null ? ownerId : "anon");

        if (ownerId == null || ownerId.isBlank()) {
            userPlan = KieruUtil.SubscriptionPlan.ANONYMOUS.getName();
        }
        else {
            String cachedPlan = redisTemplate.opsForValue().get(redisOwnerSubscriptionKey);
            if (cachedPlan != null) {
                userPlan = cachedPlan;
            } else {
                userPlan = userRepo.getSubscriptionById(ownerId);
                if (userPlan == null) userPlan = KieruUtil.SubscriptionPlan.EXPLORER.getName();
                redisTemplate.opsForValue().set(redisOwnerSubscriptionKey, userPlan, 1, TimeUnit.HOURS);
            }
        }
        String todayString = LocalDate.now().toString();
        KieruUtil.SubscriptionPlan planEnum = KieruUtil.SubscriptionPlan.getEnumByName(userPlan);
        int dailyLimit = kieruUtil.getDailyCreateLimit(planEnum);

        String limitKey;
        if (ownerId != null && !ownerId.isBlank()) {
            limitKey = redisKeyUtil.buildKey(RedisKeyUtil.KeyType.RATE_LIMIT_DAILY_USER, ownerId, todayString);
        } else {
            limitKey = redisKeyUtil.buildKey(RedisKeyUtil.KeyType.RATE_LIMIT_DAILY_IP, ipAddress, todayString);
        }

        Long limitUsed = redisTemplate.opsForValue().increment(limitKey);
        if (limitUsed != null && limitUsed == 1) {
            redisTemplate.expire(limitKey, 24, TimeUnit.HOURS);
        }

        if(limitUsed != null && limitUsed > dailyLimit){
            return SecretMetadataResponseDTO.builder()
                    .isSuccess(false).httpStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .message("Daily Limit Reached").build();
        }

        String id = securityUtil.generateRandomId(10);

        SecretMetadata meta = new SecretMetadata();
        meta.setId(id);
        meta.setOwnerId(ownerId);
        meta.setSecretName(request.getSecretName());
        meta.setMaxViews(request.getMaxViews());
        meta.setShowTimeBomb(request.isShowTimeBomb());
        meta.setExpiresAt(Instant.ofEpochMilli(request.getExpiresAt()));
        meta.setViewTimeSeconds(request.getViewTimeSeconds());
        meta.setCreatedAt(Instant.now());
        meta.setActive(true);
        meta.setViewsLeft(request.getMaxViews()); // Initialize DB count

        metaRepo.saveAndFlush(meta);

        SecretPayload payload = new SecretPayload();
        payload.setId(id);
        payload.setMetadata(meta);
        payload.setEncryptedContent(request.getContent());
        payload.setType(request.getType());

        if(request.getPassword() != null && !request.getPassword().isBlank()){
            payload.setPasswordHash(securityUtil.hashPassword(request.getPassword()));
        }

        payloadRepo.save(payload);

        long ttlSeconds = (request.getExpiresAt() - System.currentTimeMillis()) / 1000;
        if (ttlSeconds > 0) {
            String redisKey = redisKeyUtil.buildKey(RedisKeyUtil.KeyType.VIEWS_LEFT) + id;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(meta.getMaxViews()), ttlSeconds, TimeUnit.SECONDS);
        }

        return SecretMetadataResponseDTO.builder()
                .secretId(meta.getId())
                .secretName(meta.getSecretName())
                .expiresAt(meta.getExpiresAt())
                .maxViews(meta.getMaxViews())
                .viewTimeInSeconds(meta.getViewTimeSeconds())
                .httpStatus(HttpStatus.CREATED)
                .build();
    }

    @Override
    public SecretResponseDTO getSecretContent(String id, String password, Instant accessedAt, String ipAddress, String userAgent) {

        String blockKey = redisKeyUtil.buildKey(RedisKeyUtil.KeyType.RATE_LIMIT_FAILED_ATTEMPT, id, ipAddress);
        String failedAttempts = redisTemplate.opsForValue().get(blockKey);

        if (failedAttempts != null && Integer.parseInt(failedAttempts) >= 5) {
            return SecretResponseDTO.builder().isSuccess(false).message("Too many failed attempts. Blocked for 1 hour.").build();
        }

        // --- 2. METADATA CHECK ---
        Optional<SecretMetadata> optionalMeta = metaRepo.findById(id);
        if (optionalMeta.isEmpty()) {
            return SecretResponseDTO.builder().isSuccess(false).message("Secret Not Found.").build();
        }

        SecretMetadata meta = optionalMeta.get();

        if (meta.isDeleted()) {
            handleFailureAsync(id, "This Secret was deleted", accessedAt, ipAddress, userAgent);
            return SecretResponseDTO.builder().isSuccess(false).isDeleted(true).message("This Secret was deleted").build();
        }

        if (meta.getExpiresAt().isBefore(accessedAt)) {
            String msg = "Expired by Time.";
            // Async: Disable & Log
            accessLogService.disableSecretAsync(id);
            handleFailureAsync(id, msg, accessedAt, ipAddress, userAgent);
            return SecretResponseDTO.builder().isSuccess(false).isExpired(true).expiresAt(meta.getExpiresAt()).message(msg).build();
        }

        if (!meta.isActive()) {
            String msg = "Secret is no longer active.";
            handleFailureAsync(id, msg, accessedAt, ipAddress, userAgent);
            return SecretResponseDTO.builder().isSuccess(false).isActive(false).viewsLeft(meta.getViewsLeft()).message(msg).build();
        }

        Optional<SecretPayload> optionalPayload = payloadRepo.findById(id);
        if (optionalPayload.isEmpty()) {
            return SecretResponseDTO.builder().isSuccess(false).message("Data integrity error.").build();
        }

        SecretPayload payload = optionalPayload.get();
        String storedHashPassword = payload.getPasswordHash();

        if (storedHashPassword != null && !storedHashPassword.isBlank()) {
            if (!securityUtil.verifyPassword(password, storedHashPassword)) {
                // FAILED: Increment Block Counter
                Long attempts = redisTemplate.opsForValue().increment(blockKey);
                if (attempts != null && attempts == 1) {
                    redisTemplate.expire(blockKey, 1, TimeUnit.HOURS);
                }

                String msg = "Invalid Password";
                handleFailureAsync(id, msg, accessedAt, ipAddress, userAgent);
                return SecretResponseDTO.builder().isSuccess(false).isValidationPassed(false).message(msg).build();
            }
        }

        String redisViewKey = redisKeyUtil.buildKey(RedisKeyUtil.KeyType.VIEWS_LEFT, id);
        Long viewsLeft = redisTemplate.opsForValue().decrement(redisViewKey);

        // Redis Recovery Logic
        if (viewsLeft == null || viewsLeft < 0) {
             // If Redis was empty (0), decrement makes it -1.
             // We MUST check the DB truth.
             int dbViews = meta.getViewsLeft();

             if (dbViews > 0) {
                 // RECOVERY: Redis lost memory, but DB says we have views.
                 // Current request uses 1, so reset Redis to (DB - 1).
                 viewsLeft = (long) (dbViews - 1);

                 long ttl = meta.getExpiresAt().getEpochSecond() - (System.currentTimeMillis() / 1000);
                 if (ttl > 0) redisTemplate.opsForValue().set(redisViewKey, String.valueOf(viewsLeft), ttl, TimeUnit.SECONDS);
             } else {
                 // DB confirms 0 views. Limit reached.
                 // Treat as blocked.
                 viewsLeft = -1L;
             }
        }

        if (viewsLeft >= 0) {
            int finalViews = viewsLeft.intValue();
            boolean shouldDisable = (finalViews == 0);

            // Async: Update DB & Log Success
            accessLogService.updateSecretStateAsync(id, finalViews, shouldDisable);

            CreateAccessLog log = buildLogDTO(true, "Success", accessedAt, ipAddress, userAgent);
            accessLogService.saveLogAsync(log, id);

            return SecretResponseDTO.builder()
                    .isSuccess(true)
                    .type(String.valueOf(payload.getType()))
                    .content(payload.getEncryptedContent())
                    .viewsLeft(finalViews)
                    .viewTimeSeconds(meta.getViewTimeSeconds())
                    .showTimeBomb(meta.isShowTimeBomb())
                    .expiresAt(meta.getExpiresAt())
                    .build();
        }
        else {
            String msg = "Max views reached";
            // Async: Ensure Disabled & Log Failure
            accessLogService.disableSecretAsync(id);
            handleFailureAsync(id, msg, accessedAt, ipAddress, userAgent);

            return SecretResponseDTO.builder().isSuccess(false).message(msg).build();
        }
    }

    // --- PRIVATE HELPERS ---

    private void handleFailureAsync(String id, String msg, Instant time, String ip, String ua) {
        CreateAccessLog log = buildLogDTO(false, msg, time, ip, ua);
        accessLogService.saveLogAsync(log, id);
    }

    private CreateAccessLog buildLogDTO(boolean success, String msg, Instant time, String ip, String ua) {
        return CreateAccessLog.builder()
                .accessedAt(time != null ? time : Instant.now())
                .wasSuccessful(success)
                .failureReason(msg)
                .userAgent(ua)
                .ipAddress(ip)
                .build();
    }


    @Override
    public List<SecretMetadataResponseDTO> getMySecretsMeta(String ownerId, int startOffset, int limit, boolean onlyActive) {
        Pageable pageable = PageRequest.of(startOffset / limit, limit);
        List<SecretMetadata> metedataList = metaRepo.findByOwnerIdAndIsActive(ownerId, onlyActive, pageable);

        return metedataList.stream().map( data ->
                SecretMetadataResponseDTO.builder().secretId(data.getId()).secretName(data.getSecretName())
                        .maxViews(data.getMaxViews()).currentViews(data.getMaxViews() - data.getViewsLeft())
                        .createdAt(data.getCreatedAt()).expiresAt(data.getExpiresAt()).showTimeBomb(data.isShowTimeBomb())
                        .viewTimeInSeconds(data.getViewTimeSeconds()).isActive(data.isActive()).build()
        ).toList();
    }


    public SecretLogsResponseDTO getTop50SecretLogs(String secretId) {
        return getSecretLogs(secretId, 0, 50);
    }

    public SecretLogsResponseDTO getSecretLogs(String secretId, int pageNo, int pageSize){
        Pageable pageable = PageRequest.of(pageNo / pageSize, pageSize );
        return getSecretLogs(secretId, pageable);
    }


    @Override
    public SecretLogsResponseDTO getSecretLogs(String secretId, Pageable pageable) {
        List<SecretAccessLog> accessLogs = logRepo.findBySecret_IdOrderByAccessedAtDesc(secretId, pageable);

        List<SecretLogsResponseDTO.LogEntry> logsEntry = accessLogs.stream().map( log ->
                SecretLogsResponseDTO.LogEntry.builder().ipAddress(log.getIpAddress()).deviceType(log.getDeviceType())
                        .userAgent(log.getUserAgent()).accessedAt(log.getAccessedAt()).wasSuccessful(log.isWasSuccessful()).build()
        ).toList();

        return SecretLogsResponseDTO.builder().isSuccess(true).logs(logsEntry).httpStatus(HttpStatus.OK).build();
    }

    @Override
    public SecretMetadataResponseDTO deleteSecret(String secretId) {
        metaRepo.deleteById(secretId); // Standard JPA
        return SecretMetadataResponseDTO.builder().secretId(secretId).isSuccess(true)
                .message("Secret Deleted Successfully").httpStatus(HttpStatus.OK).build();
    }

    @Override
    public SecretResponseDTO updateSecretPassword(String secretId, String newPassword) {
        String hashedPassword = securityUtil.hashPassword(newPassword);
        SecretPayload payload = payloadRepo.findById(secretId).orElseThrow(() -> new RuntimeException("Secret not found"));
        payload.setPasswordHash(hashedPassword);
        payloadRepo.save(payload);

        return SecretResponseDTO.builder().id(payload.getId()).isSuccess(true).httpStatus(HttpStatus.OK).build();
    }

    private void saveAccessLog(CreateAccessLog accessLog) {
        try {
            SecretAccessLog log = new SecretAccessLog();

            SecretMetadata ref = metaRepo.getReferenceById(accessLog.getId());
            log.setSecret(ref);

            log.setAccessedAt(Instant.now());
            log.setIpAddress(accessLog.getIpAddress());
            log.setUserAgent(accessLog.getUserAgent());
            log.setWasSuccessful(accessLog.isWasSuccessful());
            log.setFailureReason(accessLog.getFailureReason());

            logRepo.save(log);
        }
        catch (Exception e) {
            System.err.println("Async Log Failed: " + e.getMessage());
        }
    }
}
