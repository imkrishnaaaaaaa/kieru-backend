package com.kieru.backend.repository;

import com.kieru.backend.dto.CreateAccessLog;
import com.kieru.backend.dto.SecretMetadataResponseDTO;
import com.kieru.backend.entity.SecretAccessLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessLogRepository extends JpaRepository<SecretAccessLog, Long> {

    /**
     * Count logs for a secret (safety-net when Redis evicts counters).
     * Translates to: SELECT COUNT(*) FROM secret_access_logs WHERE secret_id = :secretId
     */
    long countBySecret_Id(String secretId);

    /**
     * Fetch logs for a secret with pagination (for admin UI / audit).
     */
    List<SecretAccessLog> findBySecret_IdOrderByAccessedAtDesc(String secretId, Pageable pageable);

    /**
     * Fetch last N logs for a secret (small result set, convenience method).
     */
    List<SecretAccessLog> findTop50BySecret_IdOrderByAccessedAtDesc(String secretId);

    /**
     * Delete logs for a secret (used when deleting metadata).
     */
    void deleteBySecret_Id(String secretId);
}
