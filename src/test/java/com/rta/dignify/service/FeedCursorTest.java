package com.rta.dignify.service;

import com.rta.dignify.dto.feed.FeedCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FeedCursorTest {

    @Test
    @DisplayName("커서 디코딩 및 인코딩 동작 테스트")
    void cursorCodableTest() {
//        phase=1, genreOffset=5, generalOffset=0, seed=123
        Integer phase = 1;
        Integer genreOffset = 5;
        Integer generalOffset = 0;
        Integer seed = 123;
        FeedCursor cursor = new FeedCursor(phase, genreOffset, generalOffset, seed);

        String encoded = cursor.encode();
        FeedCursor decodedCursor = FeedCursor.decode(encoded);
        assertThat(decodedCursor.phase()).isEqualTo(phase);
        assertThat(decodedCursor.genreOffset()).isEqualTo(genreOffset);
        assertThat(decodedCursor.seed()).isEqualTo(seed);
        assertThat(decodedCursor.generalOffset()).isEqualTo(generalOffset);
    }

}
