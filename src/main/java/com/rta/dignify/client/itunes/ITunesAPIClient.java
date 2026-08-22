package com.rta.dignify.client.itunes;

import com.rta.dignify.dto.itunes.ItunesItem;
import com.rta.dignify.dto.itunes.ItunesLookupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ITunesAPIClient {
    private final RestClient restClient;

    public ITunesAPIClient() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.valueOf("text/javascript")
        ));

        this.restClient = RestClient.builder()
                .baseUrl("https://itunes.apple.com")
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(converter);
                })
                .build();
    }

    public List<ItunesItem> lookup(List<Long> artistIds) {
        String ids = artistIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        ItunesLookupResponse response = restClient.get()
                .uri("/lookup?id={ids}&entity=song&limit=10&sort=recent", ids)
                .retrieve()
                .body(ItunesLookupResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .filter(item -> "track".equals(item.wrapperType()))
                .filter(item -> item.previewUrl() != null)
                .toList();
    }

    // 아티스트명 → 후보 아티스트 목록. 동명이인이 있으면 여러 건이 나오므로 판단은 호출부에서.
    public List<ItunesItem> searchArtists(String artistName) {
        ItunesLookupResponse response = restClient.get()
                .uri("/search?term={term}&entity=musicArtist&country=US&limit=10", artistName)
                .retrieve()
                .body(ItunesLookupResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .filter(item -> "artist".equals(item.wrapperType()))
                .toList();
    }

    // artistId로 해당 아티스트의 트랙만 조회. 이름 검색과 달리 남의 곡이 섞이지 않는다.
    // limit은 id당 적용되며 200이 상한. 200을 넘는 카탈로그는 잘린다.
    public List<ItunesItem> lookupSongsByArtistId(long artistId) {
        ItunesLookupResponse response = restClient.get()
                .uri("/lookup?id={id}&entity=song&country=US&limit=200", artistId)
                .retrieve()
                .body(ItunesLookupResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .filter(item -> "track".equals(item.wrapperType()))
                .filter(item -> item.previewUrl() != null)
                .toList();
    }

    // US 스토어프론트로 트랙 id들을 조회한다. artistId 백필용 — 우리 카탈로그가 US 기준으로
    // 수집돼서, KR로 물으면 KR 스토어에 없는 곡이 통째로 빠진다.
    public List<ItunesItem> lookupSongsByTrackIds(List<String> trackIds) {
        String ids = String.join(",", trackIds);

        ItunesLookupResponse response = restClient.get()
                .uri("/lookup?id={ids}&entity=song&country=US", ids)
                .retrieve()
                .body(ItunesLookupResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .filter(item -> "track".equals(item.wrapperType()))
                .toList();
    }

    // KR 스토어프론트로 트랙 id들을 조회해 한글 로컬라이즈 값을 받아온다. (enrichment 크론용)
    public List<ItunesItem> lookupKrByTrackIds(List<String> trackIds) {
        String ids = String.join(",", trackIds);

        ItunesLookupResponse response = restClient.get()
                .uri("/lookup?id={ids}&entity=song&country=KR&lang=ko_kr", ids)
                .retrieve()
                .body(ItunesLookupResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .filter(item -> "track".equals(item.wrapperType()))
                .toList();
    }
}
