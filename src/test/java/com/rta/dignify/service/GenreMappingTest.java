package com.rta.dignify.service;

import com.rta.dignify.service.cron.GenreMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenreMappingTest {

    @Test
    @DisplayName("13개 장르는 그대로 통과한다")
    void canonicalGenre_passesThrough() {
        assertThat(GenreMapping.canonical("Rock")).isEqualTo("Rock");
        assertThat(GenreMapping.canonical("Hip-Hop/Rap")).isEqualTo("Hip-Hop/Rap");
        assertThat(GenreMapping.canonical("R&B/Soul")).isEqualTo("R&B/Soul");
    }

    @Test
    @DisplayName("리프 장르는 13개 중 하나로 접힌다")
    void leafGenre_foldsIntoCanonical() {
        assertThat(GenreMapping.canonical("Singer/Songwriter")).isEqualTo("Alternative");
        assertThat(GenreMapping.canonical("House")).isEqualTo("Electronic");
        assertThat(GenreMapping.canonical("Korean Indie")).isEqualTo("K-Pop");
        assertThat(GenreMapping.canonical("TV Soundtrack")).isEqualTo("Soundtrack");
    }

    @Test
    @DisplayName("iTunes가 CCM 대신 주는 Christian도 CCM으로 접힌다")
    void christian_foldsIntoCcm() {
        assertThat(GenreMapping.canonical("Christian")).isEqualTo("CCM");
        assertThat(GenreMapping.canonical("CCM")).isEqualTo("CCM");
    }

    @Test
    @DisplayName("매핑에 없는 장르는 null — 호출부가 트랙을 버린다")
    void unmappedGenre_returnsNull() {
        assertThat(GenreMapping.canonical("Chinese Opera")).isNull();
        assertThat(GenreMapping.canonical(null)).isNull();
    }

    @Test
    @DisplayName("접힌 결과는 반드시 13개 중 하나다 — 오타가 있으면 genres 조회가 조용히 실패한다")
    void foldedResult_isAlwaysCanonical() {
        String[] leaves = {
                "Bass", "Dubstep", "House", "Jungle/Drum'n'bass", "Electronica", "New Age",
                "Downtempo", "Techno", "Trance", "Garage", "Blues", "Metal", "Korean Rock",
                "Singer/Songwriter", "Indie Pop", "Indie Rock", "J-Pop", "Christmas", "Holiday",
                "Christian", "Adult Contemporary", "Rap", "Hip-Hop", "Korean Hip-Hop", "Disco", "African Dancehall",
                "Funk", "Korean Indie", "Folk", "TV Soundtrack"
        };
        for (String leaf : leaves) {
            String folded = GenreMapping.canonical(leaf);
            assertThat(folded).as("'%s' 매핑 결과", leaf).isNotNull();
            assertThat(GenreMapping.canonical(folded)).as("'%s' → '%s'는 13개 중 하나여야 한다", leaf, folded)
                    .isEqualTo(folded);
        }
    }
}
