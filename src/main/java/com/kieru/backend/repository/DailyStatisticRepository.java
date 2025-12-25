package com.kieru.backend.repository;

import com.kieru.backend.entity.DailyStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyStatisticRepository extends JpaRepository<DailyStatistic, Long> {

    // Fetch range for charts
    List<DailyStatistic> findByDateBetweenOrderByDateAsc(LocalDate startDate, LocalDate endDate);

    // Check if stats already exist for a date
    Optional<DailyStatistic> findByDate(LocalDate date);

    /**
     * Convenience to fetch recent N days (descending).
     */
    List<DailyStatistic> findTop30ByOrderByDateDesc();
}
