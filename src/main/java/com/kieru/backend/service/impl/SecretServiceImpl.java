package com.kieru.backend.service.impl;

import com.google.firestore.v1.TransactionOptions;
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
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Service
@RequiredArgsConstructor
@Slf4j
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

        MDC.put("userId", ownerId == null || ownerId.isBlank()  ? "anonymous" : ownerId);
        MDC.put("clientIp", ipAddress);

        try {
            String userPlan;
            String redisOwnerSubscriptionKey = RedisKeyUtil.buildKey(RedisKeyUtil.KeyType.SUBSCRIPTION_PLAN, ownerId != null ? ownerId : "anon");

            log.info("Create Secret :: Request to create secret. Name: [{}], Type: [{}]", request.getSecretName(), request.getType());

            if (ownerId == null || ownerId.isBlank()) {
                userPlan = KieruUtil.SubscriptionPlan.ANONYMOUS.getName();
                log.debug("Create Secret :: User is anonymous. Using default plan.");
            }
            else {
                String cachedPlan = redisTemplate.opsForValue().get(redisOwnerSubscriptionKey);
                if (cachedPlan != null) {
                    userPlan = cachedPlan;
                    log.debug("Create Secret :: Using cached plan: {}", userPlan);
                } else {
                    KieruUtil.SubscriptionPlan planEnum = userRepo.findSubscriptionPlanById(ownerId);
                    userPlan = planEnum == null ? KieruUtil.SubscriptionPlan.EXPLORER.getName() : planEnum.getName();
                    redisTemplate.opsForValue().set(redisOwnerSubscriptionKey, userPlan, 5, TimeUnit.MINUTES);
                    log.debug("Create Secret :: Fetched plan from DB: {}", userPlan);
                }
            }

            String todayString = LocalDate.now().toString();
            KieruUtil.SubscriptionPlan planEnum = KieruUtil.SubscriptionPlan.getEnumByName(userPlan);
            int dailyLimit = kieruUtil.getUserDailyCreateLimit(planEnum);

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
                log.warn("Create Secret :: Daily limit reached. Plan: {}, Limit: {}, Used: {}", userPlan, dailyLimit, limitUsed);
                return SecretMetadataResponseDTO.builder()
                        .isSuccess(false).httpStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .message("Daily Limit Reached").build();
            }

            boolean isPasswordProtected = request.getPassword() != null && !request.getPassword().isBlank();
            Instant expiryInstant = Instant.ofEpochMilli(request.getExpiresAt() != null ? request.getExpiresAt() : (System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)));
            int maxViews = request.getMaxViews() == null ? 1 : request.getMaxViews();

            String id = securityUtil.generateRandomId(10);
            log.debug("Create Secret :: Generated secret ID: {}", id);

            SecretMetadata meta = new SecretMetadata();
            meta.setId(id);
            meta.setOwnerId(ownerId);
            meta.setSecretName(request.getSecretName());
            meta.setMaxViews(maxViews);
            meta.setShowTimeBomb(request.getShowTimeBomb() != null && request.getShowTimeBomb());
            meta.setPasswordProtected(isPasswordProtected);
            meta.setExpiresAt(expiryInstant);
            meta.setViewsLeft(maxViews);
            meta.setViewTimeSeconds(request.getViewTimeSeconds() == null ? 120 : request.getViewTimeSeconds());
            meta.setCreatedAt(Instant.now());
            meta.setActive(true);
            metaRepo.saveAndFlush(meta);
            log.debug("Create Secret :: Metadata saved successfully");

            SecretPayload payload = new SecretPayload();
            payload.setMetadata(meta);
            payload.setEncryptedContent(request.getContent());
            payload.setType(request.getType());

            if(isPasswordProtected){
                payload.setPasswordHash(securityUtil.hashPassword(request.getPassword()));
            }

            payloadRepo.save(payload);
            log.debug("Create Secret :: Payload saved successfully");

            long ttlSeconds = Duration.between(Instant.now(), expiryInstant).getSeconds();
            if (ttlSeconds > 0) {
                String redisKey = RedisKeyUtil.buildKey(RedisKeyUtil.KeyType.VIEWS_LEFT) + id;
                redisTemplate.opsForValue().set(redisKey, String.valueOf(meta.getMaxViews()), ttlSeconds, TimeUnit.SECONDS);
                log.debug("Create Secret :: Redis cache set with TTL: {} seconds", ttlSeconds);
            }
            else {
                log.warn("Create Secret :: TTL is negative or zero: {} seconds, skipping Redis cache", ttlSeconds);
            }

            log.info("Create Secret :: Secret created successfully. ID: {}, PasswordProtected: {}, MaxViews: {}", id, isPasswordProtected, maxViews);

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
        finally {
            MDC.clear();
        }
    }

    @Override
    public SecretMetadataResponseDTO validateSecret(String secretId) {
        MDC.put("secretId", secretId);
        log.debug("Validate Secret :: Validating secret ID: {}", secretId);

        try {
            Optional<SecretMetadata> optionalMeta = metaRepo.findById(secretId);
            if(optionalMeta.isPresent()){
                SecretMetadata meta = optionalMeta.get();
                log.debug("Validate Secret :: Validation successful for secretId: {}", secretId);
                return SecretMetadataResponseDTO.builder()
                        .isSuccess(true)
                        .secretId(meta.getId())
                        .secretName(meta.getSecretName())
                        .isActive(meta.isActive())
                        .isPasswordProtected(meta.isPasswordProtected())
                        .viewsLeft(meta.getViewsLeft())
                        .httpStatus(HttpStatus.OK)
                        .build();
            }
            log.warn("Validate Secret :: Validation failed. Secret not found: {}", secretId);
            return SecretMetadataResponseDTO.builder().isSuccess(false).build();
        }
        finally {
            MDC.clear();
        }
    }

    @Override
    public SecretResponseDTO getSecretContent(String id, String password, Instant accessedAt, String ipAddress, String userAgent) {
        MDC.put("secretId", id);
        MDC.put("ipAddress", ipAddress);
        MDC.put("userAgent", userAgent);

        try {
            log.info("Get Secret :: Attempting to access secret ID: {}", id);

            Optional<SecretMetadata> optionalMeta = metaRepo.findById(id);

            if (optionalMeta.isEmpty()) {
                log.warn("Get Secret :: Secret not found: {}", id);
                return SecretResponseDTO.builder().isSuccess(false).message("Secret Not Found.").httpStatus(HttpStatus.NOT_FOUND).build();
            }

            SecretMetadata meta = optionalMeta.get();

            if (meta.isDeleted()) {
                String message = "This Secret was deleted";
                CreateAccessLog accessLog = CreateAccessLog.builder().secretId(id).id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                        .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
                CompletableFuture.runAsync(() -> saveAccessLog(accessLog));
                log.warn("Get Secret :: {}: {}", message, id);
                return SecretResponseDTO.builder().isSuccess(false).isDeleted(true).message(message).httpStatus(HttpStatus.GONE).build();
            }

            if (meta.getExpiresAt().isBefore(accessedAt)) {
                String message = "Expired by Time.";
                meta.setExpiresAt(Instant.now());
                metaRepo.save(meta);

                CreateAccessLog accessLog = CreateAccessLog.builder().secretId(id).id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                        .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
                CompletableFuture.runAsync(() -> saveAccessLog(accessLog));
                log.warn("Get Secret :: {}: {}", message, id);
                return SecretResponseDTO.builder().isSuccess(false).isExpired(true).expiresAt(meta.getExpiresAt()).message(message).httpStatus(HttpStatus.GONE).build();
            }

            if (!meta.isActive()) {
                String message = "Secret is no longer active.";
                CreateAccessLog accessLog = CreateAccessLog.builder().secretId(id).id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                        .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
                CompletableFuture.runAsync(() -> saveAccessLog(accessLog));
                log.warn("{}: {}", message, id);
                return SecretResponseDTO.builder().isSuccess(false).isActive(meta.isActive()).viewsLeft(meta.getViewsLeft()).message(message).httpStatus(HttpStatus.GONE).build();
            }

            Optional<SecretPayload> optionalPayload = payloadRepo.findById(id);
            if (optionalPayload.isEmpty()) {
                String message = "Data integrity error. Secret present in Meta table but missing in Payload table.";
                CreateAccessLog accessLog = CreateAccessLog.builder().secretId(id).id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                        .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
                CompletableFuture.runAsync(() -> saveAccessLog(accessLog));
                log.error("{}: {}", message, id);
                return SecretResponseDTO.builder().isSuccess(false).message(message).httpStatus(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
            SecretPayload payload = optionalPayload.get();
            String storedHashPassword = payload.getPasswordHash();

            if (storedHashPassword != null && !storedHashPassword.isBlank() && !securityUtil.verifyPassword(password, storedHashPassword)) {
                String message = "Invalid Password";
                CreateAccessLog accessLog = CreateAccessLog.builder().secretId(id).id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                        .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
                CompletableFuture.runAsync(() -> saveAccessLog(accessLog));
                log.warn("Get Secret :: {}: {}", message, id);
                return SecretResponseDTO.builder().isSuccess(false).isValidationPassed(false).message(message).httpStatus(HttpStatus.FORBIDDEN).build();
            }

            String redisKey = RedisKeyUtil.buildKey(RedisKeyUtil.KeyType.VIEWS_LEFT, id);
            Long viewsLeft = redisTemplate.opsForValue().decrement(redisKey);
            log.debug("Get Secret :: Redis views left after decrement: {}", viewsLeft);

            if (viewsLeft == null || viewsLeft < 0) {
                log.debug("Get Secret :: Redis key not found or invalid, falling back to database");

                Optional<SecretMetadata> optionalNewMeta = metaRepo.findById(id);
                if (optionalNewMeta.isEmpty()) {
                    String message = "Secret Not Found!!!";
                    CreateAccessLog accessLog = CreateAccessLog.builder().secretId(id).id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                            .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
                    CompletableFuture.runAsync(() -> saveAccessLog(accessLog));
                    log.error("Get Secret :: {}: {}", message, id);
                    return SecretResponseDTO.builder().isSuccess(false).message(message).httpStatus(HttpStatus.NOT_FOUND).build();
                }
                meta = optionalNewMeta.get();


                int dbViews = meta.getViewsLeft();
                if(dbViews <= 0) {
                    String message = "Max views reached";
                    CreateAccessLog accessLog = CreateAccessLog.builder().secretId(id).id(securityUtil.generateRandomId(10)).accessedAt(Instant.now()).wasSuccessful(false)
                            .failureReason(message).accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
                    SecretMetadata finalMeta1 = meta;
                    CompletableFuture.runAsync(() -> {
                        finalMeta1.setActive(false);
                        metaRepo.save(finalMeta1);
                        saveAccessLog(accessLog);
                    });
                    log.warn("Get Secret :: {}: {}", message, id);
                    return SecretResponseDTO.builder().isSuccess(false).message(message).httpStatus(HttpStatus.GONE).build();
                }

                viewsLeft = (long) (dbViews - 1);
                long ttl = meta.getExpiresAt().getEpochSecond() - (System.currentTimeMillis() / 1000);

                if (ttl > 0) {
                    redisTemplate.opsForValue().set(redisKey, String.valueOf(viewsLeft), ttl, TimeUnit.SECONDS);
                    log.debug("Get Secret :: Repopulated Redis cache with views: {}, TTL: {}", viewsLeft, ttl);
                }
                else {
                    log.warn("Get Secret :: TTL is negative or zero ({}), secret likely expired. Not caching.", ttl);
                    meta.setActive(false);
                    metaRepo.save(meta);
                    return SecretResponseDTO.builder()
                            .isSuccess(false)
                            .isExpired(true)
                            .expiresAt(meta.getExpiresAt())
                            .httpStatus(HttpStatus.GONE)
                            .message("Secret expired")
                            .build();
                }
            }

            int finalViews = viewsLeft.intValue();
            SecretMetadata finalMeta = meta;
            CompletableFuture.runAsync(() -> {
                finalMeta.setViewsLeft(finalViews);
                metaRepo.save(finalMeta);
                log.debug("Get Secret :: Updated views left in DB: {}", finalViews);
                if (finalViews == 0) {
                    finalMeta.setActive(false);
                    metaRepo.save(finalMeta);
                    log.info("Get Secret :: Secret marked as inactive. ID: {}", id);
                }
            });

            CreateAccessLog accessLog = CreateAccessLog.builder().secretId(id).id(securityUtil.generateRandomId(10)).secretId(meta.getId()).accessedAt(Instant.now()).wasSuccessful(true)
                    .accessedAt(accessedAt).userAgent(userAgent).ipAddress(ipAddress).build();
            CompletableFuture.runAsync(() -> saveAccessLog(accessLog));

            long timeTaken = System.currentTimeMillis() - accessedAt.toEpochMilli();
            MDC.put("timeTaken", String.valueOf(timeTaken));
            log.info("Get Secret :: Successfully accessed secret. Secret Id: {}, Views Left: {}, Time Taken: {}ms", id, finalViews, timeTaken);

            return SecretResponseDTO.builder()
                    .isSuccess(true)
                    .type(String.valueOf(payload.getType()))
                    .content(payload.getEncryptedContent())
                    .viewsLeft(finalViews)
                    .viewTimeSeconds(meta.getViewTimeSeconds())
                    .showTimeBomb(meta.isShowTimeBomb())
                    .expiresAt(meta.getExpiresAt())
                    .httpStatus(HttpStatus.OK)
                    .build();
        }
        finally {
            MDC.clear();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecretMetadataResponseDTO> getMySecretsMeta(String ownerId, int pageNumber, int pageSize, boolean onlyActive) {
        log.info("Get My Secrets :: Fetching secrets for owner: {}, pageNumber: {}, pageSize: {}, onlyActive: {}", ownerId, pageNumber, pageSize, onlyActive);

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        List<SecretMetadata> metedataList = onlyActive ? metaRepo.findByOwnerIdAndIsActive(ownerId, onlyActive, pageable) : metaRepo.findByOwnerId(ownerId, pageable);

        log.debug("Get My Secrets :: Found {} secrets for owner: {}", metedataList.size(), ownerId);

        return metedataList.stream().map( data ->
                SecretMetadataResponseDTO.builder().secretId(data.getId()).secretName(data.getSecretName())
                        .maxViews(data.getMaxViews()).currentViews(data.getMaxViews() - data.getViewsLeft()).isPasswordProtected(data.isPasswordProtected())
                        .createdAt(data.getCreatedAt()).expiresAt(data.getExpiresAt()).showTimeBomb(data.isShowTimeBomb())
                        .viewTimeInSeconds(data.getViewTimeSeconds()).isActive(data.isActive()).build()
        ).toList();
    }

    public SecretLogsResponseDTO getTop50SecretLogs(String secretId) {
        log.debug("Get Logs :: Fetching top 50 logs for secret: {}", secretId);
        return getSecretLogs(secretId, 0, 50);
    }

public SecretLogsResponseDTO getSecretLogs(String secretId, int page, int size) {
    log.debug("Get Logs :: Fetching logs for secret: {}, page: {}, size: {}", secretId, page, size);
    Pageable pageable = PageRequest.of(page, size);
    return getSecretLogs(secretId, pageable);
}

    @Override
    public SecretLogsResponseDTO getSecretLogs(String secretId, Pageable pageable) {
        log.info("Get Logs :: Fetching logs for secret: {}, page: {}, size: {}", secretId, pageable.getPageNumber(), pageable.getPageSize());

        List<SecretAccessLog> accessLogs = logRepo.findBySecret_IdOrderByAccessedAtDesc(secretId, pageable);

        log.debug("Get Logs :: Found {} access logs for secret: {}", accessLogs.size(), secretId);

        List<SecretLogsResponseDTO.LogEntry> logsEntry = accessLogs.stream().map( log ->
                SecretLogsResponseDTO.LogEntry.builder().ipAddress(log.getIpAddress()).deviceType(log.getDeviceType())
                        .userAgent(log.getUserAgent()).accessedAt(log.getAccessedAt()).wasSuccessful(log.getWasSuccessful()).failureReason(log.getFailureReason()).build()
        ).toList();

        return SecretLogsResponseDTO.builder().isSuccess(true).logs(logsEntry).httpStatus(HttpStatus.OK).build();
    }

    @Override
    @Transactional
    public SecretMetadataResponseDTO deleteSecret(String secretId) {
        log.info("Delete Secret :: Soft deleting secret ID: {}", secretId);

        SecretMetadata meta = metaRepo.findById(secretId)
                .orElseThrow(() -> {
                    log.error("Delete Secret :: Secret not found: {}", secretId);
                    return new RuntimeException("Secret not found");
                });

        meta.setDeleted(true);
        meta.setActive(false);
        metaRepo.save(meta);

        String redisKey = RedisKeyUtil.buildKey(RedisKeyUtil.KeyType.VIEWS_LEFT, secretId);
        redisTemplate.delete(redisKey);

        log.info("Delete Secret :: Secret soft-deleted successfully: {}", secretId);

        return SecretMetadataResponseDTO.builder()
                .secretId(secretId)
                .isSuccess(true)
                .message("Secret Deleted Successfully")
                .httpStatus(HttpStatus.OK)
                .build();
    }

    @Override
    public SecretResponseDTO updateSecretPassword(String secretId, String newPassword) {
        log.info("Update Password :: Updating password for secret ID: {}", secretId);

        String hashedPassword = securityUtil.hashPassword(newPassword);
        SecretPayload payload = payloadRepo.findById(secretId).orElseThrow(() -> {
            log.error("Update Password :: Secret not found: {}", secretId);
            return new RuntimeException("Secret not found");
        });

        payload.setPasswordHash(hashedPassword);
        payloadRepo.save(payload);

        log.info("Update Password :: Password updated successfully for secret ID: {}", secretId);

        return SecretResponseDTO.builder().id(payload.getId()).isSuccess(true).httpStatus(HttpStatus.OK).build();
    }

    private void saveAccessLog(CreateAccessLog accessLog) {
        try {
            log.debug("Save Access Log :: Saving access log for secret: {}", accessLog.getSecretId());

            SecretAccessLog log = new SecretAccessLog();

            SecretMetadata ref = metaRepo.findById(accessLog.getSecretId()).orElse(null);

            if (ref == null) {
                SecretServiceImpl.log.warn("Save Access Log :: Secret not found, skipping log save: {}", accessLog.getSecretId());
                return;
            }

            log.setSecret(ref);
            log.setAccessedAt(Instant.now());
            log.setIpAddress(accessLog.getIpAddress());
            log.setUserAgent(accessLog.getUserAgent());
            log.setWasSuccessful(accessLog.getWasSuccessful());
            log.setFailureReason(accessLog.getFailureReason());

            logRepo.save(log);
            SecretServiceImpl.log.debug("Save Access Log :: Access log saved successfully for secret: {}", accessLog.getSecretId());
        }
        catch (Exception e) {
            SecretServiceImpl.log.error("Save Access Log :: Failed to save access log for secret: {}", accessLog.getSecretId(), e);
        }
    }
}
