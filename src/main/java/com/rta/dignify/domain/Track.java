package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.rta.dignify.dto.itunes.ItunesItem;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Table(name = "tracks",
        uniqueConstraints = @UniqueConstraint(name = "uq_external_source", columnNames = {"external_id", "source"}),
        indexes = @Index(name = "idx_track_genre_id", columnList = "genre_id"))
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

    // KR 스토어프론트 로컬라이즈 값. enrichment 크론이 채우며, 없으면 기존 컬럼으로 폴백.
    @Column(name = "artist_name_ko", columnDefinition = "TEXT")
    private String artistNameKo;

    @Column(name = "track_name_ko", columnDefinition = "TEXT")
    private String trackNameKo;

    @Column(name = "collection_name_ko", columnDefinition = "TEXT")
    private String collectionNameKo;

    @Column(name = "track_view_url_ko", columnDefinition = "TEXT")
    private String trackViewUrlKo;

    // enrichment 크론이 이 row의 KR lookup을 시도했는지. 매칭 실패해도 true로 찍어 재조회 방지.
    @Column(name = "ko_checked", nullable = false, columnDefinition = "boolean default false")
    private Boolean koChecked = false;

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

    private static boolean isKo(Locale locale) {
        return "ko".equals(locale.getLanguage());
    }

    public String displayArtistName(Locale locale) {
        return isKo(locale) && artistNameKo != null ? artistNameKo : artistName;
    }

    public String displayTrackName(Locale locale) {
        return isKo(locale) && trackNameKo != null ? trackNameKo : trackName;
    }

    public String displayCollectionName(Locale locale) {
        return isKo(locale) && collectionNameKo != null ? collectionNameKo : collectionName;
    }

    public String displayTrackViewUrl(Locale locale) {
        return isKo(locale) && trackViewUrlKo != null ? trackViewUrlKo : trackViewUrl;
    }

    // KR lookup 매칭 성공: 기존 값과 다른 필드만 채우고(같으면 null 유지) checked 표시.
    // 서구권 곡은 KR도 영어라 대부분 null로 남음 → 중복 저장 방지, null = "실제 한글값 있음"의 의미 유지.
    public void applyKoLocalization(String artistNameKo, String trackNameKo, String collectionNameKo, String trackViewUrlKo) {
        this.artistNameKo = differsOrNull(this.artistName, artistNameKo);
        this.trackNameKo = differsOrNull(this.trackName, trackNameKo);
        this.collectionNameKo = differsOrNull(this.collectionName, collectionNameKo);
        this.trackViewUrlKo = differsOrNull(this.trackViewUrl, trackViewUrlKo);
        this.koChecked = true;
    }

    private static String differsOrNull(String base, String ko) {
        return ko != null && !ko.equals(base) ? ko : null;
    }

    // KR lookup 매칭 실패: 재조회만 막음.
    public void markKoChecked() {
        this.koChecked = true;
    }

    // 정크 메타(카라오케/트리뷰트/사운드알라이크/힐링·피트니스 컴필) 수집 차단. 아래 SQL UPDATE와 동일 기준 유지.
    // ponytail: Cover/Instrumental/Backing Track은 정당한 곡 많아 의도적으로 제외(보류).
    private static final Pattern JUNK = Pattern.compile(
            "karaoke|tribute|originally performed|made famous by|in the style of"
                    + "|sound effects|white noise|rain sounds|nature sounds|sleep sounds"
                    + "|lullaby version|music box|8[- ]?bit|rockabye baby"
                    + "|meditation|yoga|asmr|hypnosis"
                    + "|workout|running music|spinning|bpm",
            Pattern.CASE_INSENSITIVE);

    private static boolean isJunk(ItunesItem item) {
        return JUNK.matcher(item.artistName()).find()
                || JUNK.matcher(item.collectionName()).find()
                || JUNK.matcher(item.trackName()).find();
    }

    public static Optional<Track> from(ItunesItem item, Genre genre) {
        if (item.artistName() == null || item.collectionName() == null || item.trackName() == null
                || item.trackViewUrl() == null || item.artworkUrl100() == null || item.releaseDate() == null) {
            return Optional.empty();
        }
        if (isJunk(item)) {
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
