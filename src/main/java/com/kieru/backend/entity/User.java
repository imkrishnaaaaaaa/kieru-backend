package com.kieru.backend.entity;

import com.kieru.backend.util.KieruUtil;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.checkerframework.common.aliasing.qual.Unique;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kieru_users",
        indexes = {
                @Index(name = "idx_kieru_users_email", columnList = "email")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_kieru_users_email", columnNames = "email")
        })
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @Column(length = 50)
    private String id;

    @Email
    @Column(nullable = true, length = 255, unique = true)
    private String email;

    @NotBlank
    @Column(length = 50, nullable = false)
    private String displayName;

    @Column(length = 512)
    private String photoUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private KieruUtil.UserRole role;

    // Added this back to ensure subscription is saved correctly
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private KieruUtil.SubscriptionPlan subscription;

    @Column(length = 50)
    private String sessionVersion;

    // Added Login Provider (Needed for your Filter logic)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private KieruUtil.LoginProvider loginProvider;

    @Column(nullable = false)
    private boolean isBanned = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @LastModifiedDate
    private Instant lastLoginAt;

    @Column(length = 50)
    private String lastLoginIp;

    @Min(0)
    @Column(nullable = false)
    private int secretsCreatedCount = 0;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
        // Default Provider/Sub if missing
        if (this.loginProvider == null) {
            this.loginProvider = KieruUtil.LoginProvider.EMAIL;
        }
        if (this.subscription == null) {
            this.subscription = KieruUtil.SubscriptionPlan.ANONYMOUS;
        }
    }
}