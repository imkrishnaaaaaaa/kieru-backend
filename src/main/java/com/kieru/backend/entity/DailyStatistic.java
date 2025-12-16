package com.kieru.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

@Entity
@Table(
        name = "daily_statistics",
        indexes = {
                @Index(name = "idx_daily_stats_date", columnList = "date")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_stats_date", columnNames = "date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyStatistic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * One row per day; unique constraint ensures we don't accidentally insert duplicates.
     * Keep this non-nullable so DB enforces the rule.
     */
    @NotNull
    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    // --- USER METRICS ---
    @Min(0)
    @Column(name = "new_users", nullable = false)
    private int newUsers = 0;        // signups during the day

    @Min(0)
    @Column(name = "active_users", nullable = false)
    private int activeUsers = 0;     // unique logins during the day

    // --- SECRET METRICS ---
    @Min(0)
    @Column(name = "secrets_created", nullable = false)
    private int secretsCreated = 0;  // links generated

    @Min(0)
    @Column(name = "secrets_viewed", nullable = false)
    private long secretsViewed = 0L; // total views (long for scale)

    // --- SUBSCRIPTION METRICS ---
    @Min(0)
    @Column(name = "new_subscriptions", nullable = false)
    private int newSubscriptions = 0;

    @Min(0)
    @Column(name = "total_active_subscriptions", nullable = false)
    private int totalActiveSubscriptions = 0;

    // --- COST METRICS ---
    @Min(0)
    @Column(name = "total_storage_bytes", nullable = false)
    private long totalStorageBytes = 0L;

    /**
     * Optional optimistic lock so aggregator jobs can safely update without stomping each other.
     * Useful if multiple workers might attempt to upsert the same day's row.
     */
    @Version
    private Long version;

    @PrePersist
    public void ensureDate() {
        if (this.date == null) {
            // use UTC date to avoid timezone ambiguity in scheduled jobs
            this.date = LocalDate.now(ZoneOffset.UTC);
        }
    }

    // equals/hashCode on date only helps to detect duplicates in memory; don't rely on it for DB identity
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DailyStatistic)) return false;
        DailyStatistic that = (DailyStatistic) o;
        return Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date);
    }
}
