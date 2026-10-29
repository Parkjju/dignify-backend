package com.rta.dignify.repository;

import com.rta.dignify.domain.WeeklyPick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface WeeklyPickRepository extends JpaRepository<WeeklyPick, Long> {
    boolean existsByUserIdAndWeekStart(Long userId, LocalDate weekStart);
}
