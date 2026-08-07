package com.rta.dignify.repository;

import com.rta.dignify.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByPickIdAndUserId(Long pickId, Long userId);
}
