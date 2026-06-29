package com.rta.dignify.repository;

import com.rta.dignify.domain.CronState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CronStateRepository extends JpaRepository<CronState, Long> {
    Optional<CronState> findByJobName(String jobName);
}
