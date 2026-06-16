package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "user_genres", uniqueConstraints = @UniqueConstraint(name = "uq_user_genre_id", columnNames = {"user_id", "genre_id"}))
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGenre extends BaseTimeEntity {

    @Id
    @Column(name = "user_genre_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Genre genre;

    private UserGenre(User user, Genre genre) {
        this.user = user;
        this.genre = genre;
    }

    public static UserGenre create(User user, Genre genre) {
        return new UserGenre(user, genre);
    }
}
