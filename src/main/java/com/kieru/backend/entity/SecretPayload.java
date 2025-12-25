package com.kieru.backend.entity;

import com.kieru.backend.util.KieruUtil;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "secret_payload")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecretPayload implements Persistable<String> {

    @Id
    @Column(length = 50)
    private String id;

    // RELATIONSHIP (Fixed)
    // 1. Removed @MapsId (We set ID manually)
    // 2. Added insertable=false, updatable=false.
    //    This tells Hibernate: "Use this field for linking, but do NOT try to save the Metadata object again."
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", referencedColumnName = "id", insertable = false, updatable = false)
    private SecretMetadata metadata;

    // hashed password (nullable if secret is public / no password)
    @Column(name = "password_hash", length = 128)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private KieruUtil.SecretType type;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    private String encryptedContent;

    // --- PERSISTABLE LOGIC (Required for Manual IDs) ---
    // This forces Hibernate to treat manual IDs as NEW rows (INSERT), not updates.

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}