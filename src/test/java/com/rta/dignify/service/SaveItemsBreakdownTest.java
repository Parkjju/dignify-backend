package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.service.cron.CronBatchService;
import com.rta.dignify.service.cron.TrackSaveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/// 적재 내역 집계. 'Red Rocks Worship 200곡 중 4곡 저장'에서 나머지 196곡이 어디로 갔는지
/// 화면이 말해주려면 이 숫자들이 맞아야 한다. found = saved + duplicate + genreDropped + incomplete.
///
/// DB를 안 붙이는 이유: TrackSaveService가 REQUIRES_NEW라 @DataJpaTest의 미커밋 트랜잭션에서는
/// 같은 테스트가 넣은 장르가 안 보여 전부 FK 위반으로 떨어진다. 여기서 볼 건 세는 로직뿐이다.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SaveItemsBreakdownTest {

    @InjectMocks CronBatchService cronBatchService;

    @Mock GenreRepository genreRepository;
    @Mock TrackRepository trackRepository;
    @Mock TrackSaveService trackSaveService;

    private ItunesItem song(long trackId, String genre) {
        return new ItunesItem("track", trackId, 1L, "artist", "album", "track-" + trackId,
                "art", "preview", "view", null, genre, "2020-01-01T00:00:00Z", "US");
    }

    private void genreExists() {
        given(genreRepository.findByGenreNameEn(anyString())).willReturn(Optional.of(Genre.create("Rock", "락")));
    }

    @Test
    @DisplayName("장르가 매핑에 없으면 드롭 수와 장르명이 함께 나온다")
    void unmappedGenre_isReportedWithName() {
        genreExists();

        CronBatchService.SaveResult r = cronBatchService.saveItems(List.of(
                song(1L, "Rock"), song(2L, "Chinese Opera"), song(3L, "Chinese Opera")));

        assertThat(r.found()).isEqualTo(3);
        assertThat(r.saved()).isEqualTo(1);
        assertThat(r.genreDropped()).isEqualTo(2);
        assertThat(r.unmappedGenres()).containsExactly("Chinese Opera");
    }

    @Test
    @DisplayName("iTunes가 CCM 대신 Christian을 줘도 이제 저장된다")
    void christianGenre_isSaved() {
        genreExists();

        assertThat(cronBatchService.saveItems(List.of(song(1L, "Christian"))).saved()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 배치 안의 중복과 이미 DB에 있는 트랙은 duplicate로 센다")
    void duplicates_areCounted() {
        genreExists();
        given(trackRepository.existsByExternalIdAndSource("1", "ITUNES")).willReturn(true);

        CronBatchService.SaveResult r = cronBatchService.saveItems(List.of(
                song(1L, "Rock"), song(2L, "Rock"), song(2L, "Rock")));

        assertThat(r.duplicate()).isEqualTo(2);
        assertThat(r.saved()).isEqualTo(1);
    }

    @Test
    @DisplayName("장르가 아예 없는 트랙도 NPE 없이 드롭으로 잡힌다")
    void nullGenre_doesNotBlowUp() {
        CronBatchService.SaveResult r = cronBatchService.saveItems(List.of(song(1L, null)));

        assertThat(r.genreDropped()).isEqualTo(1);
        assertThat(r.unmappedGenres()).containsExactly("(장르 없음)");
    }

    @Test
    @DisplayName("내역의 합은 항상 found와 같다 — 어디로 갔는지 빠짐없이 설명된다")
    void bucketsAlwaysSumToFound() {
        genreExists();

        CronBatchService.SaveResult r = cronBatchService.saveItems(List.of(
                song(1L, "Rock"), song(1L, "Rock"), song(2L, "Chinese Opera"), song(3L, null), song(4L, "Christian")));

        assertThat(r.saved() + r.duplicate() + r.genreDropped() + r.incomplete()).isEqualTo(r.found());
    }
}
