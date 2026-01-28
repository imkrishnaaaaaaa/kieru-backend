package com.kieru.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "secret_access_logs", indexes = {
        @Index(name = "idx_secret_access_logs_secret", columnList = "secret_id"),
        @Index(name = "idx_secret_access_logs_accessed_at", columnList = "accessed_at")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecretAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many logs → one SecretMetadata. Use LAZY so loading logs doesn't fetch the entire payload.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "secret_id", nullable = false)
    @JsonIgnore
    private SecretMetadata secret;

    @NotNull
    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_type", length = 100)
    private String deviceType;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    @Column(name = "was_successful", nullable = false)
    private Boolean wasSuccessful = false;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @PrePersist
    public void prePersist() {
        if (this.accessedAt == null) {
            this.accessedAt = Instant.now();
        }
    }
}
