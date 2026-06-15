package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Table(name = "users")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false, length = 20)
    private String nickname;

    @Column(name = "is_onboarding_complete", nullable = false)
    private Boolean isOnboardingComplete = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private User(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }

    public static User create(String email, String nickname) {
        return new User(email, nickname);
    }

    public void deleteUser() {
        this.deletedAt = Instant.now();
    }
}
