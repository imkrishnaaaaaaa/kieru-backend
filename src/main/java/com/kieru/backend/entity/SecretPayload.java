package com.kieru.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kieru.backend.util.KieruUtil;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "secret_payload")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecretPayload {

    @Id
    @Column(length = 50)
    private String id;

    /**
     * Shared primary-key strategy:
     * This entity uses the same PK value as its SecretMetadata (1:1 strict).
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id")
    @JsonIgnore
    private SecretMetadata metadata;

    // hashed password (nullable if secret is public / no password)
    @Column(name = "password_hash", length = 128)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private KieruUtil.SecretType type;

    /**
     * The encrypted content (could be large, e.g. base64 image).
     * Use @Lob (and columnDefinition 'text' for Postgres) to allow large payloads.
     */
    @Lob
    @NotBlank
    @Column(nullable = false, columnDefinition = "text")
    private String encryptedContent;
}
