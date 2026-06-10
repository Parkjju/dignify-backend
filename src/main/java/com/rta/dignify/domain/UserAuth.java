package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_auth", uniqueConstraints = { @UniqueConstraint(name = "uq_provider_user_id", columnNames = {"provider", "provider_user_id"})})
@Entity
@Getter
public class UserAuth extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_auth_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(length = 20, nullable = false)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    private UserAuth(User user, String provider, String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static UserAuth create(User user, String provider, String providerUserId) {
        return new UserAuth(user, provider, providerUserId);
    }
}
