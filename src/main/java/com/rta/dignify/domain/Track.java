package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.rta.dignify.dto.itunes.ItunesItem;
import java.time.Instant;
import java.util.Optional;

@Table(name = "tracks", uniqueConstraints = @UniqueConstraint(name = "uq_external_source", columnNames = {"external_id", "source"}))
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Track extends BaseTimeEntity {

    @Id
    @Column(name = "track_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "artist_name", nullable = false, columnDefinition = "TEXT")
    private String artistName;

    @Column(name = "collection_name", nullable = false, columnDefinition = "TEXT")
    private String collectionName;

    @Column(name = "track_name", nullable = false, columnDefinition = "TEXT")
    private String trackName;

    @Column(name = "preview_url", nullable = false, columnDefinition = "TEXT")
    private String previewUrl;

    // 애플뮤릭 연결 URL
    @Column(name = "track_view_url", nullable = false, columnDefinition = "TEXT")
    private String trackViewUrl;

    @Column(name = "artwork_url", nullable = false, columnDefinition = "TEXT")
    private String artworkUrl;

    @Column(name = "release_date", nullable = false)
    private Instant releaseDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Genre genre;

    @Column(length = 10)
    private String country;

    @Column(length = 10, nullable = false)
    private String source = "ITUNES";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    private Track(String externalId, String artistName, String collectionName, String trackName, String previewUrl, String trackViewUrl, String artworkUrl, Instant releaseDate, Genre genre, String country, String source) {
        this.externalId = externalId;
        this.artistName = artistName;
        this.collectionName = collectionName;
        this.trackName = trackName;
        this.previewUrl = previewUrl;
        this.trackViewUrl = trackViewUrl;
        this.artworkUrl = artworkUrl;
        this.releaseDate = releaseDate;
        this.genre = genre;
        this.country = country;
        this.source = source;
    }

    public static Track create(String externalId, String artistName, String collectionName, String trackName, String previewUrl, String trackViewUrl, String artworkUrl, Instant releaseDate, Genre genre, String country, String source) {
        return new Track(externalId, artistName, collectionName, trackName, previewUrl, trackViewUrl, artworkUrl, releaseDate, genre, country, source);
    }

    public static Optional<Track> from(ItunesItem item, Genre genre) {
        if (item.artistName() == null || item.collectionName() == null || item.trackName() == null
                || item.trackViewUrl() == null || item.artworkUrl100() == null || item.releaseDate() == null) {
            return Optional.empty();
        }
        return Optional.of(new Track(
                String.valueOf(item.trackId()),
                item.artistName(),
                item.collectionName(),
                item.trackName(),
                item.previewUrl(),
                item.trackViewUrl(),
                item.artworkUrl100(),
                Instant.parse(item.releaseDate()),
                genre,
                item.country(),
                "ITUNES"
        ));
    }
}
