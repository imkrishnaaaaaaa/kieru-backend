package com.kieru.backend.filter;


import com.kieru.backend.util.RedisKeyUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyUtil redisKeyUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Identify the Client (IP Address)
        String ipAddress = getClientIp(request);

        // 2. Identify the Target (Secret ID)
        // We only care about Brute Force on the "/access" endpoint
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/secrets/") && uri.endsWith("/access")) {

            // Extract Secret ID from URL: /api/secrets/{id}/access
            String[] parts = uri.split("/");
            if (parts.length >= 4) {
                String secretId = parts[3];

                // 3. CHECK BLOCK LIST
                // Key: limit:failed:{secretId}:{ip}
                String blockKey = redisKeyUtil.buildKey(RedisKeyUtil.KeyType.RATE_LIMIT_FAILED_ATTEMPT, secretId, ipAddress);
                String failedAttempts = redisTemplate.opsForValue().get(blockKey);

                if (failedAttempts != null && Integer.parseInt(failedAttempts) >= 5) {
                    // 🛑 BLOCKED! Return 429 Too Many Requests
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.getWriter().write("Too many failed attempts. You are blocked for 1 hour.");
                    return; // Stop the chain here!
                }
            }
        }

        // 4. Pass to Next Filter (Controller)
        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}