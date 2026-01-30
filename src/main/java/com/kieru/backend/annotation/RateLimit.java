package com.kieru.backend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    RateLimitType type() default RateLimitType.USER;
    int requests() default 100;
    int windowSeconds() default 60;
    int lockDurationMinutes() default 5; // Can be SpEL expression
}

