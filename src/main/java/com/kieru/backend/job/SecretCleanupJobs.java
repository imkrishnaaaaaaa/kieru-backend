package com.kieru.backend.job;

import com.kieru.backend.entity.SecretMetadata;
import com.kieru.backend.repository.SecretMetadataRepository;
import com.kieru.backend.util.KieruUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecretCleanupJobs {

    private final SecretMetadataRepository metaRepo;

    /**
     * Job: Expire Secrets
     * Runs every 5 minutes.
     * Scans for secrets that have passed their expiration time and marks them as inactive.
     * Processes in batches of 100 to avoid long transactions.
     *
     * sec   min   hour   day   month   weekday
     *    |     |     |      |      |        |
     *  " 0     0     12     * * * "
     *
     */

    @Scheduled(cron = "0 0 */6 * * *") // Every 6 hours
    @Transactional
    public void expireSecretsBatch() {
        MDC.put("job", "ExpireSecrets");
        long startTime = System.currentTimeMillis();
        log.info("ExpireSecretsJob :: Job Started: Scanning for expired secrets...");

        int totalProcessed = 0;
        int batchSize = 100;
        List<SecretMetadata> expiredSecrets;

        do {
            Pageable limit = PageRequest.of(0, batchSize);
            Instant now = Instant.now();

            expiredSecrets = metaRepo.findExpiredSecrets(now, limit);

            if (expiredSecrets.isEmpty()) {
                break;
            }

            for (SecretMetadata secret : expiredSecrets) {
                secret.setActive(false);
                log.debug("ExpireSecretsJob :: Marking secret {} as expired.", secret.getId());
            }

            metaRepo.saveAll(expiredSecrets);

            totalProcessed += expiredSecrets.size();

            if (totalProcessed > 10000) {
                log.warn("ExpireSecretsJob :: Job Safety Stop: Processed 10,000 secrets. Will continue next run.");
                break;
            }

        } while (expiredSecrets.size() >= batchSize); // Continue if we filled the batch

        long duration = System.currentTimeMillis() - startTime;

        if (totalProcessed > 0) {
            log.info("ExpireSecretsJob :: Expire Secrets Job :: Finished. Expired {} secrets in {} on {}.",
                    totalProcessed,
                    KieruUtil.millisToRelativeTime(duration),
                    KieruUtil.millisToDateString(System.currentTimeMillis())
            );
        }
        else {
            log.info("ExpireSecretsJob :: Finished. No expired secrets found.");
        }
    }
}