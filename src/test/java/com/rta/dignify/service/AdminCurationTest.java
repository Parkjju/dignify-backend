package com.rta.dignify.service;

import com.rta.dignify.domain.CurationTrack;
import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.global.config.JpaAuditingConfig;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.repository.ArtistRequestRepository;
import com.rta.dignify.repository.CurationTrackRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// 어드민 화면이 세트를 통째로 덮어쓰는 경로. 순서가 뒤집히거나 이전 세트가 남으면
/// 앱 대문에 엉뚱한 곡이 걸리므로 여기서 막는다.
/// iTunes 클라이언트는 이 경로에서 안 쓰이므로 null로 넣고 서비스를 직접 만든다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
public class AdminCurationTest {

    @Autowired TestEntityManager entityManager;
    @Autowired CurationTrackRepository curationTrackRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired ArtistRequestRepository artistRequestRepository;
    @Autowired UserDeviceTokenRepository userDeviceTokenRepository;

    AdminService adminService;
    List<Track> tracks;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(curationTrackRepository, trackRepository, artistRequestRepository, userDeviceTokenRepository, null);

        Genre genre = Genre.create("Rock", "락");
        entityManager.persistAndFlush(genre);
        tracks = List.of(track(genre, "a"), track(genre, "b"), track(genre, "c"));
        tracks.forEach(entityManager::persistAndFlush);
    }

    private Track track(Genre genre, String key) {
        return Track.create(key, "artist-" + key, "album-" + key, "track-" + key,
                "https://preview/" + key, "https://view/" + key, "https://art/" + key, Instant.now(), genre, "US", "itunes");
    }

    @Test
    @DisplayName("보낸 순서대로 앞에 나온다")
    void keepsGivenOrder() {
        List<FeedItem> set = adminService.replaceCurationSet(List.of(tracks.get(2).getId(), tracks.get(0).getId()));

        assertThat(set).extracting(FeedItem::trackId)
                .containsExactly(tracks.get(2).getId(), tracks.get(0).getId());
    }

    @Test
    @DisplayName("목록에 없던 곡은 세트에서 내려간다")
    void dropsMissingTracks() {
        adminService.replaceCurationSet(List.of(tracks.get(0).getId(), tracks.get(1).getId()));

        List<FeedItem> set = adminService.replaceCurationSet(List.of(tracks.get(1).getId()));

        assertThat(set).extracting(FeedItem::trackId).containsExactly(tracks.get(1).getId());
        // 행은 남고 꺼지기만 한다 — track_id가 unique라 되살릴 자리가 필요하다.
        assertThat(curationTrackRepository.findAll()).hasSize(2)
                .filteredOn(CurationTrack::getIsActive).hasSize(1);
    }

    @Test
    @DisplayName("빈 목록이면 세트가 비워진다")
    void clearsSet() {
        adminService.replaceCurationSet(List.of(tracks.get(0).getId()));

        assertThat(adminService.replaceCurationSet(List.of())).isEmpty();
    }

    @Test
    @DisplayName("없는 트랙 id가 섞이면 통째로 막는다")
    void rejectsUnknownTrackId() {
        assertThatThrownBy(() -> adminService.replaceCurationSet(List.of(tracks.get(0).getId(), 999999L)))
                .isInstanceOf(BusinessException.class);
    }
}
