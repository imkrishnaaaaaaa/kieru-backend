package com.kieru.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "secret_metadata",
        indexes = {
                @Index(name = "idx_secret_metadata_owner", columnList = "owner_id"),
                @Index(name = "idx_secret_metadata_expires", columnList = "expires_at")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecretMetadata {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "owner_id", length = 50)
    private String ownerId;

    @Column(length = 50)
    private String secretName;

    @Min(0)
    @Column(name = "max_views", nullable = false)
    private int maxViews = 1;

    @Min(0)
    @Column(name = "views_left", nullable = false)
    private int viewsLeft = 1;

    @Column(name = "view_time_seconds", nullable = false)
    private int viewTimeSeconds = 60;

    @Column(name = "show_time_bomb", nullable = false)
    private boolean showTimeBomb = false;

    @Column(name = "is_password_protected", nullable = false)
    private boolean passwordProtected = false;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @OneToOne(mappedBy = "metadata", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private SecretPayload payload;

    @OneToMany(mappedBy = "secret", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonIgnore
    private List<SecretAccessLog> accessLogs = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.expiresAt == null) {
            this.expiresAt = Instant.now().plusSeconds(24 * 3600);
        }
    }
}
