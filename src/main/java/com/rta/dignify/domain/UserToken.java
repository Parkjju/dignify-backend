package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_tokens")
@Entity
@Getter
public class UserToken extends BaseTimeEntity  {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    private User user;

    // SHA-256으로 리프레시 토큰 해시 후 저장 -> length 64로 고정
    @Column(name = "refresh_token_hash", nullable = false, length = 64, unique = true)
    private String refreshTokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    private UserToken(User user, String refreshTokenHash, Instant expiresAt) {
        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
    }

    public static UserToken create(User user, String refreshTokenHash, Instant expiresAt) {
        return new UserToken(user, refreshTokenHash, expiresAt);
    }
}
