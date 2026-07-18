package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "artist_requests", indexes = @Index(name = "idx_artist_request_user_id", columnList = "user_id"))
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistRequest extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "artist_name", nullable = false, length = 100)
    private String artistName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "cancel_reason")
    private String cancelReason;

    private ArtistRequest(User user, String artistName) {
        this.user = user;
        this.artistName = artistName;
        this.status = RequestStatus.PENDING;
    }

    public static ArtistRequest create(User user, String artistName) {
        return new ArtistRequest(user, artistName);
    }

    public void resolve(RequestStatus status, String cancelReason) {
        this.status = status;
        this.cancelReason = cancelReason;
    }
}
