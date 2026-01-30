package com.kieru.backend.service.impl;

import com.kieru.backend.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryConsume(String key, int limit, int windowSeconds) {
        try {
            Long current = redisTemplate.opsForValue().increment(key);

            if (current == null) {
                log.warn("RateLimit :: Redis increment returned null for key: {}", key);
                return false;
            }

            if (current == 1) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
                log.debug("RateLimit :: Set TTL for key: {}, window: {}s", key, windowSeconds);
            }

            boolean allowed = current <= limit;
            log.debug("Rate limit check - Key: {}, Current: {}, Limit: {}, Allowed: {}",
                key, current, limit, allowed);

            return allowed;

        } catch (Exception e) {
            log.error("RateLimit :: Rate limit check failed for key: {}", key, e);
            return true;
        }
    }

    @Override
    public void lock(String identifier, int durationMinutes) {
        String lockKey = "ratelimit:lock:" + identifier;
        redisTemplate.opsForValue().set(lockKey, "locked", durationMinutes, TimeUnit.MINUTES);
    }

    @Override
    public boolean isLocked(String identifier) {
        String lockKey = "ratelimit:lock:" + identifier;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
}