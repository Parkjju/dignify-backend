package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
@Entity
@Getter
public class User extends BaseTimeEntity  {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false, length = 20)
    private String nickname;

    @Column(name = "is_onboarding_complete", nullable = false)
    private Boolean isOnboardingComplete = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
