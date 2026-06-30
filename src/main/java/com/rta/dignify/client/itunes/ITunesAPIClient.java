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
}
