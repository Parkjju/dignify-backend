package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_genres", uniqueConstraints = @UniqueConstraint(name = "uq_user_genre_id", columnNames = {"user_id", "genre_id"}))
@Entity
@Getter
public class UserGenre extends BaseTimeEntity  {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_genre_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", nullable = false, updatable = false)
    private Genre genre;

    private UserGenre(User user, Genre genre) {
        this.user = user;
        this.genre = genre;
    }

    public static UserGenre create(User user, Genre genre) {
        return new UserGenre(user, genre);
    }
}
