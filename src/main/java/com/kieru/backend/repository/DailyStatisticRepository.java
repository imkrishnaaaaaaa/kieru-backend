package com.kieru.backend.repository;

import com.kieru.backend.entity.DailyStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyStatisticRepository extends JpaRepository<DailyStatistic, Long> {

    // Fetch range for charts
    List<DailyStatistic> findByDateBetweenOrderByDateAsc(LocalDate startDate, LocalDate endDate);

    /**
     * Convenience to fetch recent N days (descending).
     */
    List<DailyStatistic> findTop30ByOrderByDateDesc();

    Optional<DailyStatistic> findByDate(LocalDate date);

    // Cleanup Query
    @Modifying
    @Query("DELETE FROM DailyStatistic d WHERE d.date < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDate cutoff);
}
