package com.kieru.backend.util;

import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class RedisKeyUtil {

    private static final String SEPARATOR = ":";

    @Getter
    public enum KeyType {
        VIEWS_LEFT("views:left"),
        SUBSCRIPTION_PLAN("subscription:plan"),
        RATE_LIMIT_DAILY_USER("limit:daily:user"),
        RATE_LIMIT_DAILY_IP("limit:daily:ip"),
        RATE_LIMIT_FAILED_ATTEMPT("limit:failed"),

        // --- IDEMPOTENCY ---
        IDEMPOTENCY_KEY("processed");  // Usage: processed:{requestId}

        private final String prefix;

        KeyType(String prefix) {
            this.prefix = prefix;
        }
    }

    /**
     * Generates a consistent Redis key.
     * * @param key The category of the key (from Enum).
     * @param args The dynamic parts (ID, IP, Date, etc).
     * @return A joined string like "limit:daily:user:u123"
     */
    public static String buildKey(KeyType key, String... args) {
        if (key == null) {
            throw new IllegalArgumentException("Redis KeyType cannot be null");
        }

        // Start with the key (e.g., "views")
        StringBuilder keyBuilder = new StringBuilder(key.getPrefix());

        // Append all dynamic arguments with the separator
        for (String arg : args) {
            if (arg != null && !arg.isBlank()) {
                keyBuilder.append(SEPARATOR).append(arg);
            }
        }

        return keyBuilder.toString();
    }
}