package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.Getter;

@Table(name = "cron_state")
@Entity
@Getter
public class CronState extends BaseTimeEntity {

    @Id
    @Column(name = "job_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, unique = true)
    private String jobName;

    @Column(name = "last_processed_id")
    private Long lastProcessedId;
}
