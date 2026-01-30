package com.kieru.backend.job;

import com.kieru.backend.repository.UserRepository;
import com.kieru.backend.util.KieruUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCleanupJob {

    private final UserRepository userRepo;

    /**
     * Logic: Remove ANONYMOUS/EXPLORER users inactive for 180 days.
     * Runs daily at 01:30 AM
     */
    @Scheduled(cron = "0 30 1 * * *")
    @Transactional
    public void removeInactiveUsers() {
        MDC.put("job", "UserCleanup");
        log.info("RemoveInactiveUsersJob :: Starting cleanup of inactive registered users (180 days)...");

        try {
            Instant cutoff = Instant.now().minus(180, ChronoUnit.DAYS);
            List<KieruUtil.SubscriptionPlan> targetPlans = List.of(
                    KieruUtil.SubscriptionPlan.ANONYMOUS,
                    KieruUtil.SubscriptionPlan.EXPLORER
            );

            int deletedCount = userRepo.deleteInactiveUsers(targetPlans, cutoff);

            if (deletedCount > 0) {
                log.warn("RemoveInactiveUsersJob :: Cleanup Complete: Removed {} inactive users (older than {}).", deletedCount, cutoff);
            }
            else {
                log.info("RemoveInactiveUsersJob :: Cleanup Complete: No inactive users found.");
            }
        }
        finally {
            MDC.clear();
        }
    }

    /**
     * Logic: Remove pure ANONYMOUS users inactive for 30 days.
     * Runs daily at 01:35 AM (Offset by 5 mins to reduce load spike)
     */
    @Scheduled(cron = "0 35 1 * * *")
    @Transactional
    public void removeAnonymousUsers() {
        MDC.put("job", "AnonCleanup");
        log.info("RemoveAnonymousUsersJob :: Starting cleanup of anonymous sessions (30 days)...");

        try {
            Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
            // Strictly target ANONYMOUS only
            List<KieruUtil.SubscriptionPlan> targetPlans = List.of(KieruUtil.SubscriptionPlan.ANONYMOUS);

            int deletedCount = userRepo.deleteInactiveUsers(targetPlans, cutoff);

            if (deletedCount > 0) {
                log.warn("RemoveAnonymousUsersJob :: Cleanup Complete: Purged {} anonymous sessions.", deletedCount);
            }
            else {
                log.info("RemoveAnonymousUsersJob :: Cleanup Complete: No expired anonymous sessions.");
            }
        }
        finally {
            MDC.clear();
        }
    }
}