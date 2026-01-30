package com.kieru.backend.job;

import com.kieru.backend.entity.User;
import com.kieru.backend.repository.UserRepository;
import com.kieru.backend.util.KieruUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class SystemMaintenanceJob {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepo;

    // Hardcoded ID for the maintenance bot user
    private static final String SYSTEM_BOT_ID = "system_maintenance_bot";

    /**
     * Job: Infrastructure Heartbeat (Write Mode)
     * Runs daily at 02:00 AM.
     * Purpose: Performs a WRITE operation to DB and Redis to reset inactivity timers.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void sendHeartbeats() {
        MDC.put("job", "SystemHeartbeat");
        log.info("Maintenance: Starting write-based heartbeats...");

        // 1. Redis Write Heartbeat
        try {
            String key = "system:heartbeat";
            String value = Instant.now().toString();

            redisTemplate.opsForValue().set(key, value, 10, TimeUnit.MINUTES);

            log.info("SendHeartBeats :: Redis Write Success (Key: {}, Value: {})", key, value);
        }
        catch (Exception e) {
            log.error("SendHeartBeats :: Redis Write FAILED", e);
        }

        // 2. Database Write Heartbeat (Update a dedicated user row)
        try {
            updateDatabaseHeartbeat();
            log.info("SendHeartBeats :: Database Write Success (Updated Bot LastLogin)");
        }
        catch (Exception e) {
            log.error("SendHeartBeats :: Database Write FAILED", e);
        }
        finally {
            MDC.clear();
        }
    }

    /**
     * Helper: Updates a specific 'Bot' user row.
     * If the bot doesn't exist, it creates it.
     * This guarantees an INSERT or UPDATE occurs.
     */
    private void updateDatabaseHeartbeat() {

        User botUser = userRepo.findById(SYSTEM_BOT_ID).orElse(null);

        if (botUser == null) {
            botUser = User.builder()
                    .id(SYSTEM_BOT_ID)
                    .displayName("System Maintenance Bot")
                    .email(null)
                    .role(KieruUtil.UserRole.ADMIN)
                    .subscription(KieruUtil.SubscriptionPlan.ANONYMOUS)
                    .loginProvider(KieruUtil.LoginProvider.UNKNOWN)
                    .isBanned(true)
                    .joinedAt(Instant.now())
                    .lastLoginAt(Instant.now())
                    .build();
            log.info("SendHeartBeats :: Creating new System Bot user for heartbeat.");
        }
        else {
            botUser.setLastLoginAt(Instant.now());
        }

        userRepo.save(botUser);
    }
}