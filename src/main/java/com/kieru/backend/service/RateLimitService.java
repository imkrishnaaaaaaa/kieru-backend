package com.kieru.backend.service;

public interface RateLimitService {
    public boolean tryConsume(String key, int limit, int windowSeconds);

    public void lock(String identifier, int durationMinutes);

    public boolean isLocked(String identifier);
}
