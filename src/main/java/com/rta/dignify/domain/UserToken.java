package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Table(name = "user_tokens")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserToken extends BaseTimeEntity  {

    @Id
    @Column(name = "token_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    // SHA-256으로 리프레시 토큰 해시 후 저장 -> length 64로 고정
    @Column(name = "refresh_token_hash", nullable = false, length = 64, unique = true)
    private String refreshTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private UserToken(User user, String refreshTokenHash, Instant expiresAt) {
        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
    }

    public static UserToken create(User user, String refreshTokenHash, Instant expiresAt) {
        return new UserToken(user, refreshTokenHash, expiresAt);
    }

    /**
     *
     * @param newRefreshTokenHash 갱신된 리프레시 토큰
     * @param newExpiresAt 갱신된 만료 시각
     */
    public void rotate(String newRefreshTokenHash, Instant newExpiresAt) {
        this.refreshTokenHash = newRefreshTokenHash;
        this.expiresAt = newExpiresAt;
    }
}
