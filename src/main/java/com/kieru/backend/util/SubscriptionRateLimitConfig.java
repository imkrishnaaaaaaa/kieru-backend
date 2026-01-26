package com.kieru.backend.util;

import lombok.Data; // Changed to @Data for full Get/Set/ToString support
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.subscription")
@Data
public class SubscriptionRateLimitConfig {


    private Anonymous anonymous = new Anonymous();
    private Explorer explorer = new Explorer();
    private Challenger challenger = new Challenger();
    private Dominator dominator = new Dominator();
    private Tester tester = new Tester();

    @Data // Use @Data for inner classes too
    public static class Anonymous {
        private int createLimitDaily;
        private int createLimitWeekly;
        private int createLimitMonthly;
        private int charLimit;
        private int fileSizeLimit;
    }

    @Data
    public static class Explorer {
        private int createLimitDaily;
        private int createLimitWeekly;
        private int createLimitMonthly;
        private int charLimit;
        private int fileSizeLimit;
    }

    @Data
    public static class Challenger {
        private int createLimitDaily;
        private int createLimitWeekly;
        private int createLimitMonthly;
        private int charLimit;
        private int fileSizeLimit;
    }

    @Data
    public static class Dominator {
        private int createLimitDaily;
        private int createLimitWeekly;
        private int createLimitMonthly;
        private int charLimit;
        private int fileSizeLimit;
    }

    @Data
    public static class Tester {
        private int createLimitDaily;
        private int createLimitWeekly;
        private int createLimitMonthly;
        private int charLimit;
        private int fileSizeLimit;
    }
}