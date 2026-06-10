package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "cron_state")
@Getter
public class CronState extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long id;

    @Column(name = "job_name", nullable = false, unique = true)
    private String jobName;

    @Column(name = "last_processed_id")
    private Long lastProcessedId;
}
