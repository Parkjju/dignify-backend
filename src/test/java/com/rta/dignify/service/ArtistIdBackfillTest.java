package com.rta.dignify.service;

import com.rta.dignify.client.itunes.ITunesAPIClient;
import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.admin.ArtistIdBatch;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.service.cron.ArtistIdBackfillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ArtistIdBackfillTest {
    @InjectMocks
    private AdminService adminService;

    @Mock
    private ITunesAPIClient iTunesAPIClient;

    @Mock
    private ArtistIdBackfillService artistIdBackfillService;

    private Track track(long id, String externalId) {
        Track track = Track.create(externalId, "A", "Album", "Song", "preview", "view", "art",
                Instant.parse("2020-01-01T00:00:00Z"), null, "US", "ITUNES");
        ReflectionTestUtils.setField(track, "id", id);
        return track;
    }

    /// 커서가 안 나가면 화면이 같은 190곡을 영원히 다시 집는다 — 못 찾은 곡은 계속 미채움으로
    /// 남아서 조건만으로는 큐가 안 줄기 때문이다. 이 테스트가 그 무한루프를 막는다.
    @Test
    @DisplayName("커서는 못 찾은 곡이 섞여 있어도 배치의 마지막 track_id까지 나간다")
    void cursor_advancesPastUnmatched() {
        given(artistIdBackfillService.peekMissing(anyLong(), anyInt()))
                .willReturn(List.of(track(10L, "111"), track(27L, "222")));
        given(artistIdBackfillService.applyArtistIds(List.of("111", "222"), List.of()))
                .willReturn(1);   // 한 곡만 매칭됐다고 치자
        given(artistIdBackfillService.countMissing()).willReturn(41L);

        ArtistIdBatch batch = adminService.backfillArtistIdBatch(0);

        assertThat(batch.cursor()).isEqualTo(27L);
        assertThat(batch.checked()).isEqualTo(2);
        assertThat(batch.matched()).isEqualTo(1);
        assertThat(batch.remaining()).isEqualTo(41L);
    }

    @Test
    @DisplayName("커서 뒤에 남은 곡이 없으면 checked=0으로 끝난다")
    void emptyBatch_stops() {
        given(artistIdBackfillService.peekMissing(anyLong(), anyInt())).willReturn(List.of());
        given(artistIdBackfillService.countMissing()).willReturn(3L);

        ArtistIdBatch batch = adminService.backfillArtistIdBatch(99);

        assertThat(batch.checked()).isZero();
        assertThat(batch.cursor()).isEqualTo(99L);
        assertThat(batch.remaining()).isEqualTo(3L);   // iTunes에서 못 찾은 곡은 남는다
    }

    @Test
    @DisplayName("이미 artistId가 있는 곡은 백필이 덮어쓰지 않는다")
    void existingArtistId_isNotOverwritten() {
        Track collected = Track.from(new ItunesItem("track", 1L, 500L, "Queen", "Album", "Song",
                "art", "preview", "view", null, "Rock", "2020-01-01T00:00:00Z", "US"), null).orElseThrow();

        collected.backfillArtistId(999L);

        assertThat(collected.getArtistId()).isEqualTo(500L);
    }
}
