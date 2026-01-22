package com.kieru.backend.service.impl;

import com.kieru.backend.dto.*;
import com.kieru.backend.entity.SecretAccessLog;
import com.kieru.backend.entity.SecretMetadata;
import com.kieru.backend.entity.SecretPayload;
import com.kieru.backend.repository.AccessLogRepository;
import com.kieru.backend.repository.SecretMetadataRepository;
import com.kieru.backend.repository.SecretPayloadRepository;
import com.kieru.backend.repository.UserRepository;
import com.kieru.backend.service.SecretService;
import com.kieru.backend.util.KieruUtil;
import com.kieru.backend.util.RedisKeyUtil;
import com.kieru.backend.util.SecurityUtil;
import jakarta.transaction.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

    @Override
    @Transactional
    public SecretMetadataResponseDTO createSecret(CreateSecretRequest request, String ownerId, String ipAddress) {

        String userPlan;
        String redisOwnerSubscriptionKey = RedisKeyUtil.buildKey(RedisKeyUtil.KeyType.SUBSCRIPTION_PLAN, ownerId != null ? ownerId : "anon");

        if (ownerId == null || ownerId.isBlank()) {
            userPlan = KieruUtil.SubscriptionPlan.ANONYMOUS.getName();
        }
        else {
            String cachedPlan = redisTemplate.opsForValue().get(redisOwnerSubscriptionKey);
            if (cachedPlan != null) {
                userPlan = cachedPlan;
            } else {
                KieruUtil.SubscriptionPlan planEnum = userRepo.findSubscriptionPlanById(ownerId);
                userPlan = planEnum == null ? KieruUtil.SubscriptionPlan.EXPLORER.getName() : planEnum.getName();
                redisTemplate.opsForValue().set(redisOwnerSubscriptionKey, userPlan, 1, TimeUnit.HOURS);
            }
        }
        String todayString = LocalDate.now().toString();
        KieruUtil.SubscriptionPlan planEnum = KieruUtil.SubscriptionPlan.getEnumByName(userPlan);
        int dailyLimit = kieruUtil.getDailyCreateLimit(planEnum);

        String limitKey;
        if (ownerId != null && !ownerId.isBlank()) {
            limitKey = RedisKeyUtil.buildKey(RedisKeyUtil.KeyType.RATE_LIMIT_DAILY_USER, ownerId, todayString);
        } else {
            limitKey = RedisKeyUtil.buildKey(RedisKeyUtil.KeyType.RATE_LIMIT_DAILY_IP, ipAddress, todayString);
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
        meta.setShowTimeBomb(request.getShowTimeBomb());
        meta.setExpiresAt(Instant.ofEpochMilli(request.getExpiresAt()));
        meta.setViewTimeSeconds(request.getViewTimeSeconds());
        meta.setCreatedAt(Instant.now());
        meta.setActive(true);
        metaRepo.saveAndFlush(meta);

        SecretPayload payload = new SecretPayload();
        payload.setMetadata(meta);
        payload.setEncryptedContent(request.getContent());
        payload.setType(request.getType());

        if(request.getPassword() != null && !request.getPassword().isBlank()){
            payload.setPasswordHash(securityUtil.hashPassword(request.getPassword()));
        }

        payloadRepo.save(payload);

        long ttlSeconds = (request.getExpiresAt() - System.currentTimeMillis()) / 1000;
        if (ttlSeconds > 0) {
            String redisKey = RedisKeyUtil.buildKey(RedisKeyUtil.KeyType.VIEWS_LEFT) + id;
            redisTemplate.opsForValue().set(redisKey, String.valueOf(meta.getMaxViews()), ttlSeconds, TimeUnit.SECONDS);
        }

        return SecretMetadataResponseDTO.builder()
                .secretId(meta.getId())
                .secretName(meta.getSecretName())
                .expiresAt(meta.getExpiresAt())
                .maxViews(meta.getMaxViews())
                .isSuccess(true)
                .viewTimeInSeconds(meta.getViewTimeSeconds())
                .httpStatus(HttpStatus.CREATED)
                .build();
    }

    @Override
    public SecretResponseDTO getSecretContent(String id, String password, Instant accessedAt, String ipAddress, String userAgent) {

        // 1. Meta Check
        Optional<SecretMetadata> optionalMeta = metaRepo.findById(id); // Custom 'AndIsActiveTrue' removed to handle detailed errors

        if (optionalMeta.isEmpty()) {
            return SecretResponseDTO.builder().isSuccess(false).message("Secret Not Found.").build();
        }

        SecretMetadata meta = optionalMeta.get();


        if (meta.isDeleted()) {
            String message = "This Secret was deleted";
            CreateAccessLog accessLog = CreateAccessLog.builder().id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                    .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
            CompletableFuture.runAsync(() -> saveAccessLog(accessLog));
            return SecretResponseDTO.builder().isSuccess(false).isDeleted(true).message(message).build();
        }

        if (meta.getExpiresAt().isBefore(accessedAt)) {
            String message = "Expired by Time.";
            CompletableFuture.runAsync(() -> {
                meta.setExpiresAt(Instant.now());
                metaRepo.save(meta);
            });

            CreateAccessLog accessLog = CreateAccessLog.builder().id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                    .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
            CompletableFuture.runAsync(() -> saveAccessLog(accessLog));
            return SecretResponseDTO.builder().isSuccess(false).isExpired(true).expiresAt(meta.getExpiresAt()).message(message).build();
        }

        if (!meta.isActive()) {
            String message = "Secret is no longer active.";
            CreateAccessLog accessLog = CreateAccessLog.builder().id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                    .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
            CompletableFuture.runAsync(() -> saveAccessLog(accessLog));
            return SecretResponseDTO.builder().isSuccess(false).isActive(meta.isActive()).viewsLeft(meta.getViewsLeft()).message(message).build();
        }

        Optional<SecretPayload> optionalPayload = payloadRepo.findById(id);
        if (optionalPayload.isEmpty()) {
            return SecretResponseDTO.builder().isSuccess(false).message("Data integrity error.").build();
        }
        SecretPayload payload = optionalPayload.get();
        String storedHashPassword = payload.getPasswordHash();

        if (storedHashPassword != null && !storedHashPassword.isBlank() && !securityUtil.verifyPassword(password, storedHashPassword)) {
            String message = "Invalid Password";
            CreateAccessLog accessLog = CreateAccessLog.builder().id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                    .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
            CompletableFuture.runAsync(() -> saveAccessLog(accessLog));

            return SecretResponseDTO.builder().isSuccess(false).isValidationPassed(false).message(message).build();
        }

        String redisKey = RedisKeyUtil.buildKey(RedisKeyUtil.KeyType.VIEWS_LEFT, id);
        Long viewsLeft = redisTemplate.opsForValue().decrement(redisKey);
        if (viewsLeft == null || viewsLeft < -50) { // IF KEY NOT EXIST IN REDIS | <-100 is just a sanity check for weird start values
            int dbViews = metaRepo.getViewsLeftById(id);
            if(dbViews <= 0) {
                return SecretResponseDTO.builder().isSuccess(false).message("Max views reached").build();
            }

            viewsLeft = (long) (dbViews - 1);
            long ttl = meta.getExpiresAt().getEpochSecond() - (System.currentTimeMillis() / 1000);
            redisTemplate.opsForValue().set(redisKey, String.valueOf(viewsLeft), ttl, TimeUnit.SECONDS);
        }

        if (viewsLeft >= 0) {
            int finalViews = viewsLeft.intValue();
            CompletableFuture.runAsync(() -> {
                meta.setViewsLeft(finalViews);
                metaRepo.save(meta);
                if (finalViews == 0) {
                    meta.setActive(false);
                }
            });

            CreateAccessLog accessLog = CreateAccessLog.builder().id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(true)
                    .accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
            CompletableFuture.runAsync(() -> saveAccessLog(accessLog));

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
            String message = "Max views reached";
            CreateAccessLog accessLog = CreateAccessLog.builder().id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                    .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
            CompletableFuture.runAsync(() -> {
                meta.setActive(false);
                metaRepo.save(meta);
                saveAccessLog(accessLog);
            });

            return SecretResponseDTO.builder().isSuccess(false).message(message).build();
        }


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
                        .userAgent(log.getUserAgent()).accessedAt(log.getAccessedAt()).wasSuccessful(log.getWasSuccessful()).build()
        ).toList();

        return SecretLogsResponseDTO.builder().isSuccess(true).logs(logsEntry).httpStatus(HttpStatus.OK).build();
    }

    @Override
    public SecretMetadataResponseDTO deleteSecret(String secretId) {
        CompletableFuture.runAsync(() -> metaRepo.deleteById(secretId));

        return SecretMetadataResponseDTO.builder().secretId(secretId).isSuccess(true)
                .message("Secret Deleted Successfully").httpStatus(HttpStatus.OK).build();

    }

    @Override
    public SecretResponseDTO updateSecretPassword(String secretId, String newPassword) {

        String hashedPassword = securityUtil.hashPassword(newPassword);
        SecretPayload payload = payloadRepo.findById(secretId).orElseThrow(() -> new RuntimeException("Secret not found"));
        payload.setPasswordHash(hashedPassword);
        payloadRepo.save(payload); // Corrected: Use save() instead of updatePasswordById

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
            log.setWasSuccessful(accessLog.getWasSuccessful());
            log.setFailureReason(accessLog.getFailureReason());

            logRepo.save(log);
        }
        catch (Exception e) {
            System.err.println("Async Log Failed: " + e.getMessage());
        }
    }
}
