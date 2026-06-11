package com.rta.dignify.repository;

import com.rta.dignify.domain.CronState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CronStateRepository extends JpaRepository<CronState, Long> {
}
