package com.kieru.backend.aspect;

import com.kieru.backend.annotation.RateLimit;
import com.kieru.backend.entity.User;
import com.kieru.backend.exception.RateLimitExceededException;
import com.kieru.backend.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;
    private final HttpServletRequest request;

    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        log.debug("Rate limit check for endpoint: {}", getEndpoint());

        String identifier = getUserId();
        if (rateLimitService.isLocked(identifier)) {
            log.warn("Request blocked - user is locked: {}", identifier);
            throw new RateLimitExceededException("Account temporarily locked");
        }

        String key = buildRateLimitKey(rateLimit);

        boolean allowed = rateLimitService.tryConsume(
                key,
                rateLimit.requests(),
                rateLimit.windowSeconds()
        );

        if(!allowed){
            // Lock the user for X minutes
            int lockDuration = Integer.parseInt(rateLimit.lockDurationMinutes());
            rateLimitService.lock(identifier, lockDuration);

            log.warn("Rate limit exceeded - locking user: {} for {} minutes", identifier, lockDuration);
            throw new RateLimitExceededException("Too many requests. Account locked for " + lockDuration + " minutes");
        }

        log.debug("Rate limit check passed for key: {}", key);
        return joinPoint.proceed();
    }

    private String buildRateLimitKey(RateLimit rateLimit) {
        String identifier = switch (rateLimit.type()) {
            case USER -> getUserId();
            case IP, ANONYMOUS -> getClientIp();
            case GLOBAL -> "global";
        };

        return String.format("ratelimit:%s:%s:%s",
                rateLimit.type().name().toLowerCase(),
                getEndpoint(),
                identifier
        );
    }

    private String getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }

        return getClientIp();
    }

    private String getClientIp() {
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null) return cfIp;

        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }

    private String getEndpoint() {
        return request.getMethod() + ":" + request.getRequestURI();
    }
}