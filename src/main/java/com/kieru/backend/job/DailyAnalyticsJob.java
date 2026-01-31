package com.kieru.backend.job;

import com.kieru.backend.entity.DailyStatistic;
import com.kieru.backend.repository.*;
import com.kieru.backend.util.KieruUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyAnalyticsJob {

    private final DailyStatisticRepository statsRepo;
    private final UserRepository userRepo;
    private final SecretMetadataRepository secretRepo;
    private final AccessLogRepository logRepo;

    /**
     * ETL JOB: Generate Daily Statistics
     * Runs at 00:30 UTC every day.
     * Processes data for "Yesterday".
     */
    @Scheduled(cron = "0 30 0 * * *")
    @Transactional // Atomic Transaction: All stats save or nothing saves
    public void generateDailyStats() {
        MDC.put("job", "DailyAnalytics");
        long startTime = System.currentTimeMillis();

        // 1. TRANSFORM: Define the Time Window (Yesterday 00:00:00 to 23:59:59 UTC)
        // We run at 00:30 Today, so we look back 1 day.
        LocalDate targetDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);

        Instant startOfDay = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = targetDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);

        log.info("GenerateDailyStatsJob :: Starting Analytics ETL for Date: {}", targetDate);

        try {
            // 2. EXTRACT: Gather metrics from all sources
            // User Metrics
            long newUsers = userRepo.countByJoinedAtBetween(startOfDay, endOfDay);
            long activeUsers = userRepo.countByLastLoginAtBetween(startOfDay, endOfDay);

            // Secret Metrics
            long secretsCreated = secretRepo.countByCreatedAtBetween(startOfDay, endOfDay);
            long storageBytes = secretRepo.sumStorageBytesBetween(startOfDay, endOfDay);

            // Access Metrics
            long viewsSuccess = logRepo.countByAccessedAtBetweenAndWasSuccessfulTrue(startOfDay, endOfDay);
            long viewsFailed = logRepo.countByAccessedAtBetweenAndWasSuccessfulFalse(startOfDay, endOfDay);

            // 3. IDEMPOTENCY CHECK (Load Strategy)
            DailyStatistic stats = statsRepo.findByDate(targetDate)
                    .orElse(DailyStatistic.builder().date(targetDate).build());

            stats.setNewUsers((int) newUsers);
            stats.setActiveUsers((int) activeUsers);
            stats.setSecretsCreated((int) secretsCreated);
            stats.setTotalStorageBytes(storageBytes);
            stats.setSecretsViewed(viewsSuccess);
            stats.setSecretsFailedViews(viewsFailed);

            stats.setNewSubscriptions(0);
            stats.setTotalActiveSubscriptions(0);

            statsRepo.save(stats);

            log.info("GenerateDailyStatsJob :: Analytics ETL Finished. Stats for {}: [Users: {}, Secrets: {}, Views: {}]",
                    targetDate, newUsers, secretsCreated, viewsSuccess);

        }
        catch (Exception e) {
            log.error("GenerateDailyStatsJob :: Analytics ETL Failed", e);
            throw e; // Ensure Transaction rolls back
        }
        finally {
            long duration = System.currentTimeMillis() - startTime;
            MDC.put("duration_ms", String.valueOf(duration));
            log.error("GenerateDailyStatsJob :: Daily analtics job completed for date: {} total time taken: {}", targetDate, KieruUtil.millisToRelativeTime(duration));
            MDC.clear();
        }
    }

    /**
     * CLEANUP JOB: Retention Policy
     * Runs at 02:00 UTC every day.
     * Deletes analytics rows older than 180 days.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldStats() {
        MDC.put("job", "AnalyticsCleanup");
        log.info("CleanupOldStatsJob :: Starting Analytics Cleanup...");

        long startTime = System.currentTimeMillis();
        try {
            // Calculate Cutoff Date (Today - 180 Days)
            LocalDate cutoffDate = LocalDate.now(ZoneOffset.UTC).minusDays(180);

            int deletedCount = statsRepo.deleteOlderThan(cutoffDate);

            if (deletedCount > 0) {
                long duration = System.currentTimeMillis() - startTime;
                MDC.put("duration_ms", String.valueOf(duration));
                log.warn("CleanupOldStatsJob :: Cleanup Complete: Deleted {} old statistic records (older than {}).", deletedCount, cutoffDate);
            }
            else {
                log.info("CleanupOldStatsJob :: Cleanup Complete: No old records found.");
            }
        }
        finally {
            log.info("CleanupOldStatsJob :: Analytics Cleanup Completed...!");
            MDC.clear();
        }
    }
}