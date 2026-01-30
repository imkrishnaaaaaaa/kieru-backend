package com.kieru.backend.controller;

import com.kieru.backend.annotation.RateLimit;
import com.kieru.backend.annotation.RateLimitType;
import com.kieru.backend.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetsController {

    private final AssetService assetService;

    /**
     * 1. GET Subscription Plan Names
     * Returns: { "ANONYMOUS": "anonymous", "EXPLORER": "explorer", ... }
     * Cache: 1 Day (These rarely change)
     */
    @GetMapping("/subscriptions")
    @RateLimit(type = RateLimitType.IP, requests = 30, windowSeconds = 300, lockDurationMinutes = 5)
    public ResponseEntity<Map<String, String>> getSubscriptions() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS))
                .body(assetService.getSubscriptionNames());
    }

    /**
     * 2. GET Character Limits
     * Returns: { "anonymous": 500, "explorer": 750, ... }
     * Cache: 1 Day
     */
    @GetMapping("/subscriptions/char-limits")
    @RateLimit(type = RateLimitType.IP, requests = 30, windowSeconds = 300, lockDurationMinutes = 5) // 30/5mins
    public ResponseEntity<Map<String, Integer>> getSubscriptionCharLimits() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS))
                .body(assetService.getCharLimits());
    }

    /**
     * 3. GET File Size Limits
     * Returns: { "anonymous": 0, "explorer": 5242880, ... }
     * Cache: 1 Day
     */
    @GetMapping("/subscriptions/file-size-limits")
    @RateLimit(type = RateLimitType.IP, requests = 30, windowSeconds = 300, lockDurationMinutes = 5) // 30/5mins
    public ResponseEntity<Map<String, Long>> getSubscriptionFileLimits() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS))
                .body(assetService.getFileSizeLimits());
    }

    /**
     * 3. GET File Size Limits
     * Returns: { "anonymous": 0, "explorer": 5242880, ... }
     * Cache: 1 Day
     */
    @GetMapping("/subscriptions/daily-secret-limits")
    @RateLimit(type = RateLimitType.IP, requests = 30, windowSeconds = 300, lockDurationMinutes = 5) // 30/5mins
    public ResponseEntity<Map<String, Integer>> getSubscriptionDailyLimits() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(24, TimeUnit.HOURS))
                .body(assetService.getDailySecretLimits());
    }
}