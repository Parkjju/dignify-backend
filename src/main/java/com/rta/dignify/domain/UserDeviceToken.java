package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "user_device_tokens", uniqueConstraints = @UniqueConstraint(name = "uq_device_token", columnNames = "token"), indexes = @Index(name = "idx_device_token_user_id", columnList = "user_id"))
@Entity @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeviceToken extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_device_token_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false, length = 20)
    private String environment;

    private UserDeviceToken(User user, String token, String environment) {
        this.user = user;
        this.token = token;
        this.environment = environment;
    }

    public static UserDeviceToken create(User user, String token, String environment) {
        return new UserDeviceToken(user, token, environment);
    }

    public void reassign(User user, String environment) {
        this.user = user;
        this.environment = environment;
    }
}