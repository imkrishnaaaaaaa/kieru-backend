package com.kieru.backend.repository;

import com.kieru.backend.entity.SecretPayload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecretPayloadRepository extends JpaRepository<SecretPayload, String> {

    /**
     * Lookup payload by metadata id (since payload uses shared PK / mapsId).
     * Equivalent to: SELECT * FROM secret_payload WHERE id = :metadataId
     */
    Optional<SecretPayload> findByMetadata_Id(String metadataId);

//    SecretPayload updatePasswordById(String id, String password);
//
//    String getPasswordHashById(String id);
//
//    /**
//     * Delete by metadata id (convenience).
//     */
//    void deleteByMetadata_Id(String metadataId);
}
