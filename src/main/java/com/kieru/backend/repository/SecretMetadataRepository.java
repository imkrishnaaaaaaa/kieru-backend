package com.kieru.backend.repository;

import com.kieru.backend.entity.SecretMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface SecretMetadataRepository extends JpaRepository<SecretMetadata, String> {

    List<SecretMetadata> findByOwnerIdAndIsActive(String ownerId, boolean isActive, Pageable pageable);
    List<SecretMetadata> findByOwnerId(String ownerId, Pageable pageable);

    void deleteById(String id);


    /**
     * Strictly disables the secret.
     * Used when Redis hits 0.
     */
    @Modifying
    @Transactional
    @Query("UPDATE SecretMetadata s SET s.isActive = false WHERE s.id = :id")
    void disableSecret(@Param("id") String id);

    @Query("SELECT s FROM SecretMetadata s WHERE s.isActive = true AND s.expiresAt < :now")
    List<SecretMetadata> findExpiredSecrets(@Param("now") Instant now, Pageable pageable);

    // Extract: Secrets Created
    long countByCreatedAtBetween(Instant start, Instant end);

    // Extract: Storage Used (Sum of encrypted payload size)
    // Note: Joining with Payload table for byte size
    @Query("SELECT COALESCE(SUM(LENGTH(p.encryptedContent)), 0) FROM SecretMetadata m " +
            "JOIN m.payload p WHERE m.createdAt BETWEEN :start AND :end")
    long sumStorageBytesBetween(@Param("start") Instant start, @Param("end") Instant end);
}
