package com.kieru.backend.job;

import com.kieru.backend.entity.SecretMetadata;
import com.kieru.backend.repository.SecretMetadataRepository;
import com.kieru.backend.util.KieruUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class SecretCleanupJob {

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

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void expireSecretsBatch() {
        long startTime = System.currentTimeMillis();
        log.info("Job Started: Scanning for expired secrets...");

        Pageable limit = PageRequest.of(0, 100);
        Instant now = Instant.now();

        List<SecretMetadata> expiredSecrets = metaRepo.findExpiredSecrets(now, limit);

        if (expiredSecrets.isEmpty()) {
            // Logs current date correctly now
            log.info("Expire Secrets Job :: Finished. No expired secrets found. Date: {}",
                    KieruUtil.millisToDateString(System.currentTimeMillis()));
            return;
        }

        if (expiredSecrets.size() >= 100) {
            log.warn("Expire Secrets Job :: Batch limit (100) reached. More secrets may remain.");
        }

        for (SecretMetadata secret : expiredSecrets) {
            secret.setActive(false);
            log.debug("Marking secret {} as expired.", secret.getId());
        }

        metaRepo.saveAll(expiredSecrets);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Expire Secrets Job :: Finished. Expired {} secrets in {}.",
                expiredSecrets.size(),
                KieruUtil.millisToRelativeTime(duration));
    }
}