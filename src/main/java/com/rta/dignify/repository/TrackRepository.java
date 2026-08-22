package com.rta.dignify.repository;

import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.admin.GenreStat;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {
    // 활성 큐레이션 곡은 세트(/feed/curation)가 따로 앞세우므로 일반 피드에서 뺀다.
    // 예전엔 같은 조인으로 priority DESC 정렬해 끌어올렸는데, 그대로 두면 세트에서 한 번
    // 보고 일반 피드 첫 장에서 또 만난다. 끌어올리기가 세트로 대체된 셈이다.
    @Query(value = "SELECT t.* FROM tracks t " +
            "LEFT JOIN users_hype_tracks uht ON t.track_id = uht.track_id AND uht.user_id = :userId " +
            "JOIN user_genres ug ON ug.genre_id = t.genre_id AND ug.user_id = :userId " +
            "LEFT JOIN curation_tracks c ON c.track_id = t.track_id AND c.is_active IS TRUE " +
            "WHERE uht.user_hype_track_id IS NULL AND t.is_active IS TRUE AND c.curation_track_id IS NULL " +
            "ORDER BY md5(t.track_id::text || ':' || CAST(:seed AS text)) " +
            "LIMIT :limit " +
            "OFFSET :offset", nativeQuery = true)
    List<Track> findByGenreIdsExceptHypedTrackWithLimitAndOffset(@Param("userId") Long userId, @Param("limit") Integer limit, @Param("offset") Integer offset, @Param("seed") Integer seed);

    @Query(value = "SELECT t.* FROM tracks t " +
            "LEFT JOIN users_hype_tracks uht ON t.track_id = uht.track_id AND uht.user_id = :userId " +
            "LEFT JOIN user_genres ug ON ug.genre_id = t.genre_id AND ug.user_id = :userId " +
            "LEFT JOIN curation_tracks c ON c.track_id = t.track_id AND c.is_active IS TRUE " +
            "WHERE uht.user_hype_track_id IS NULL AND t.is_active IS TRUE AND ug.user_genre_id IS NULL " +
            "AND c.curation_track_id IS NULL " +
            "ORDER BY md5(t.track_id::text || ':' || CAST(:seed AS text)) " +
            "LIMIT :limit " +
            "OFFSET :offset", nativeQuery = true)
    List<Track> findGeneralTracksByGenreIdsExceptHypedTrackWithLimitAndOffset(@Param("userId") Long userId, @Param("limit") Integer limit, @Param("offset") Integer offset, @Param("seed") Integer seed);

    /// 검색어와 컬럼 양쪽에서 라틴 발음기호를 뗀다. "rosalia"로 쳐도 "ROSALÍA"가 걸리게.
    /// 자바 쪽(FeedService.foldAccents)이 같은 표를 그대로 쓰므로 양쪽 결과가 항상 일치한다.
    /// ponytail: unaccent 확장 대신 기본 함수 translate. 확장은 로컬/CI/운영 DB에 각각 손으로
    /// 깔아야 하고 운영 계정 권한도 확인 안 됐다. 1:1 치환이라 ß→ss 같은 확장은 안 된다.
    String FOLD_FROM = "àáâãäåèéêëìíîïòóôõöøùúûüýÿñçšžğıāēīōūąęćčńłśźżřđ";
    String FOLD_TO = "aaaaaaeeeeiiiioooooouuuuyyncszgiaeiouaeccnlszzrd";
    /// CAST가 없으면 Hibernate가 FUNCTION()의 반환형을 Object로 봐서 LIKE 좌변으로 못 쓴다.
    String ARTIST = "CAST(FUNCTION('translate', LOWER(t.artistName), '" + FOLD_FROM + "', '" + FOLD_TO + "') AS String)";
    String TRACK = "CAST(FUNCTION('translate', LOWER(t.trackName), '" + FOLD_FROM + "', '" + FOLD_TO + "') AS String)";

    @Query(value = "SELECT t FROM Track t " +
            "WHERE (" + ARTIST + " LIKE LOWER(CONCAT('%', :searchKeyword, '%')) OR " + TRACK + " LIKE LOWER(CONCAT('%', :searchKeyword, '%')) " +
            "OR LOWER(t.artistNameKo) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) OR LOWER(t.trackNameKo) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) ) AND t.isActive = TRUE " +
            // 관련도 티어를 1차, 아티스트명을 2차 정렬키로 둬서 같은 아티스트 곡을 한 덩어리로 모은다.
            // 정확 매칭 아티스트가 top 클러스터, 그다음 접두/포함 순. 트랙명만 걸린 건 맨 아래.
            "ORDER BY " +
            "CASE " +
            "WHEN " + ARTIST + " = LOWER(:searchKeyword) OR LOWER(t.artistNameKo) = LOWER(:searchKeyword) THEN 0 " +
            "WHEN " + ARTIST + " LIKE LOWER(CONCAT(:searchKeyword, '%')) OR LOWER(t.artistNameKo) LIKE LOWER(CONCAT(:searchKeyword, '%')) THEN 1 " +
            "WHEN " + ARTIST + " LIKE LOWER(CONCAT('%', :searchKeyword, '%')) OR LOWER(t.artistNameKo) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) THEN 2 " +
            "ELSE 3 END, " +
            "LOWER(t.artistName), " +
            "t.id " +
            "LIMIT :limit " +
            "OFFSET :offset"
    )
    List<Track> findTracksWithSearchKeyword(@Param("searchKeyword") String searchKeyword, @Param("limit") Integer limit, @Param("offset") Integer offset);

    boolean existsByExternalIdAndSource(String externalId, String source);

    long countByIsActiveTrueAndArtistNameContainingIgnoreCase(String artistName);

    /// 어드민 현황 화면용. 곡이 하나도 없는 장르는 여기 안 나온다 — 그것도 봐야 하므로 호출부에서 채운다.
    @Query("SELECT new com.rta.dignify.dto.admin.GenreStat(g.genreNameEn, COUNT(t)) " +
            "FROM Track t JOIN t.genre g WHERE t.isActive = TRUE GROUP BY g.genreNameEn ORDER BY COUNT(t) DESC")
    List<GenreStat> countByGenre();

    @Query("SELECT t.externalId FROM Track t WHERE t.koChecked = FALSE ORDER BY t.id LIMIT :limit")
    List<String> findUncheckedExternalIds(@Param("limit") Integer limit);

    List<Track> findByExternalIdIn(List<String> externalIds);

    long countByKoCheckedFalse();

    /// artistId 백필용. iTunes에서 못 찾은 곡은 artistId가 null로 남으므로, 플래그 컬럼 없이
    /// track_id 커서로 앞으로만 훑는다 — after 없이 IS NULL만 걸면 같은 배치를 무한히 다시 집는다.
    List<Track> findByArtistIdIsNullAndIdGreaterThanOrderById(Long after, Limit limit);

    long countByArtistIdIsNull();
}
