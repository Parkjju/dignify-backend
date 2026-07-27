package com.rta.dignify.service;

import com.rta.dignify.client.itunes.ITunesAPIClient;
import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.service.cron.CronBatchService;
import com.rta.dignify.service.cron.CronService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CollectByArtistTest {
    @InjectMocks
    private CronService cronService;

    @Mock
    private ITunesAPIClient iTunesAPIClient;

    @Mock
    private CronBatchService cronBatchService;

    private ItunesItem artist(long id, String name) {
        return new ItunesItem("artist", null, id, name, null, null,
                null, null, null, "https://music.apple.com/artist/" + id, "Rock", null, "US");
    }

    private ItunesItem song(long trackId) {
        return new ItunesItem("track", trackId, 1L, "A", "Album", "Song",
                "art", "preview", "view", null, "Rock", "2020-01-01T00:00:00Z", "US");
    }

    @Test
    @DisplayName("이름이 정확히 일치하는 아티스트가 한 명이면 그 artistId로 수집한다")
    void singleExactMatch_collects() {
        given(iTunesAPIClient.searchArtists("IU")).willReturn(List.of(
                artist(409076743L, "IU"),
                artist(1450123549L, "i///u"),      // 다른 이름 — 후보에서 제외
                artist(447609175L, "I.U.")));
        given(iTunesAPIClient.lookupSongsByArtistId(409076743L)).willReturn(List.of(song(1L), song(2L)));
        given(cronBatchService.saveItems(List.of(song(1L), song(2L)))).willReturn(2);

        assertThat(cronService.collectByArtist("IU")).isEqualTo(2);
    }

    @Test
    @DisplayName("동명이인이면 수집하지 않고 중단한다")
    void duplicateNames_abort() {
        given(iTunesAPIClient.searchArtists("Silica Gel")).willReturn(List.of(
                artist(1031084591L, "Silica Gel"),
                artist(189500228L, "Silica Gel"),
                artist(1532177805L, "Silica Gel")));

        assertThat(cronService.collectByArtist("Silica Gel")).isZero();
        verify(cronBatchService, never()).saveItems(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("정확히 일치하는 후보가 없으면(로마자 표기 등) 중단한다")
    void noExactMatch_abort() {
        given(iTunesAPIClient.searchArtists("산울림")).willReturn(List.of(artist(501604942L, "Sanullim")));

        assertThat(cronService.collectByArtist("산울림")).isZero();
        verify(cronBatchService, never()).saveItems(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("대소문자와 앞뒤 공백은 무시하고 일치로 본다")
    void caseAndWhitespaceInsensitive() {
        given(iTunesAPIClient.searchArtists("radiohead")).willReturn(List.of(artist(657515L, "Radiohead")));
        given(iTunesAPIClient.lookupSongsByArtistId(657515L)).willReturn(List.of(song(1L)));
        given(cronBatchService.saveItems(List.of(song(1L)))).willReturn(1);

        assertThat(cronService.collectByArtist("  radiohead  ")).isEqualTo(1);
    }
}
