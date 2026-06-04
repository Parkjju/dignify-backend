package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_genres", uniqueConstraints = @UniqueConstraint(name = "uq_user_genre_id", columnNames = {"user_id", "genre_id"}))
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
public class UserGenre {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_genre_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", nullable = false, updatable = false)
    private Genre genre;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private UserGenre(User user, Genre genre) {
        this.user = user;
        this.genre = genre;
    }

    public static UserGenre create(User user, Genre genre) {
        return new UserGenre(user, genre);
    }
}
