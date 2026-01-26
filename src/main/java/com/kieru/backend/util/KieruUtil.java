package com.kieru.backend.util;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class KieruUtil {

    private final SubscriptionRateLimitConfig config;

    public KieruUtil(SubscriptionRateLimitConfig config){
        this.config = config;
    }

    @Value("${app.default.date_format:yyyy-MM-dd}")
    private static String defaultDateFormat;

    @Value("${app.default.time_format:HH:mm:ss}")
    private static String defaultTimeFormat;

    @Value("${app.default.time_zone:UTC}")
    private static String defaultTimeZone;

    @Getter
    public enum SubscriptionPlan {
        ANONYMOUS("anonymous"),
        EXPLORER("explorer"),
        CHALLENGER("challenger"),
        DOMINATOR("dominator"),
        TESTER("tester");

        private final String name;
        SubscriptionPlan(String plan){
            this.name = plan;
        }

        public static SubscriptionPlan getEnumByName(String planName){
            for(SubscriptionPlan plan : values()){
                if(plan.getName().equalsIgnoreCase(planName)) return plan;
            }
            return ANONYMOUS; // Default safe fallback
        }

        public static List<SubscriptionPlan> getSubscriptions(){
            return new ArrayList<>(Arrays.asList(values()));
        }
    }

    public enum LoginProvider {

        GOOGLE("google.com"),
        EMAIL("password"),
        GUEST("anonymous"),
        GITHUB("github.com"),
        FACEBOOK("facebook.com"),
        TWITTER("twitter.com"),
        APPLE("apple.com"),
        MICROSOFT("microsoft.com"),
        UNKNOWN("unknown");

        private final String firebaseId;

        LoginProvider(String firebaseId) {
            this.firebaseId = firebaseId;
        }

        public static LoginProvider fromFirebaseProvider(String firebaseId) {
            for (LoginProvider provider : values()) {
                if (provider.firebaseId.equalsIgnoreCase(firebaseId)) {
                    return provider;
                }
            }
            return UNKNOWN;
        }
    }


    public int getUserDailyCreateLimit(SubscriptionPlan plan) {
        if (plan == null) return config.getAnonymous().getCreateLimitDaily();

        return switch (plan) {
            case ANONYMOUS -> config.getAnonymous().getCreateLimitDaily();
            case EXPLORER -> config.getExplorer().getCreateLimitDaily();
            case CHALLENGER -> config.getChallenger().getCreateLimitDaily();
            case DOMINATOR -> config.getDominator().getCreateLimitDaily();
            case TESTER -> config.getTester().getCreateLimitDaily();
        };
    }

    public int getWeeklyCreateLimit(SubscriptionPlan plan) {
        return switch (plan) {
            case ANONYMOUS -> config.getAnonymous().getCreateLimitWeekly();
            case EXPLORER -> config.getExplorer().getCreateLimitWeekly();
            case CHALLENGER -> config.getChallenger().getCreateLimitWeekly();
            case DOMINATOR -> config.getDominator().getCreateLimitWeekly();
            case TESTER -> config.getTester().getCreateLimitWeekly();
        };
    }

    public int getMonthlyCreateLimit(SubscriptionPlan plan) {
        return switch (plan) {
            case ANONYMOUS -> config.getAnonymous().getCreateLimitMonthly();
            case EXPLORER -> config.getExplorer().getCreateLimitMonthly();
            case CHALLENGER -> config.getChallenger().getCreateLimitMonthly();
            case DOMINATOR -> config.getDominator().getCreateLimitMonthly();
            case TESTER -> config.getTester().getCreateLimitMonthly();
        };
    }

    public int getUserCharLimit(SubscriptionPlan plan) {
        if (plan == null) return config.getAnonymous().getCreateLimitDaily();

        return switch (plan) {
            case ANONYMOUS -> config.getAnonymous().getCharLimit(); // Fixed to use Anonymous config
            case EXPLORER -> config.getExplorer().getCharLimit();
            case CHALLENGER -> config.getChallenger().getCharLimit();
            case DOMINATOR -> config.getDominator().getCharLimit();
            case TESTER -> config.getTester().getCharLimit();
        };
    }

    public int getUserFileSizeLimit(SubscriptionPlan plan) {
        if (plan == null) return config.getAnonymous().getFileSizeLimit();

        return switch (plan) {
            case ANONYMOUS -> config.getAnonymous().getFileSizeLimit(); // Fixed to use Anonymous config
            case EXPLORER -> config.getExplorer().getFileSizeLimit();
            case CHALLENGER -> config.getChallenger().getFileSizeLimit();
            case DOMINATOR -> config.getDominator().getFileSizeLimit();
            case TESTER -> config.getTester().getFileSizeLimit();
        };
    }


    @Getter
    public enum UserRole {
        USER("user"),
        ADMIN("admin"),
        SUPER_ADMIN("super_admin"),
        TESTER("tester");

        private final String role;
        UserRole(String role){
            this.role = role;
        }
    }

    public enum SecretType { TEXT, IMAGE}

    public static String formatInstant(Instant instant) {
        return formatInstant(instant, defaultDateFormat);
    }

    public static String formatInstant(Instant instant, String pattern) {
        return formatInstant(instant, pattern, defaultTimeZone);
    }

    public static String formatInstant(Instant instant, String pattern, String timeZone) {
        if (instant == null) return null;
        if (pattern == null || pattern.isBlank()) pattern = defaultDateFormat;
        if (timeZone == null || timeZone.isBlank()) pattern = defaultTimeZone;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                .withZone(ZoneId.of(timeZone));

        return formatter.format(instant);
    }

    public static long getSecondsToEOD() {
        LocalDate today = LocalDate.now();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        long now = Instant.now().getEpochSecond();
        long eod = endOfDay.atZone(ZoneId.systemDefault()).toEpochSecond();

        return Math.max(0, eod - now);
    }


}
