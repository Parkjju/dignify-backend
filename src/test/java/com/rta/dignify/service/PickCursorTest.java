package com.rta.dignify.service;

import com.rta.dignify.dto.pick.PickCursor;
import com.rta.dignify.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PickCursorTest {
    @Test
    @DisplayName("커서 디코딩 및 인코딩 동작 테스트")
    void cursorCodableTest() {
        boolean isOfficial = false;
        Long pickId = 1L;

        PickCursor cursor = new PickCursor(isOfficial, pickId);
        String encoded = cursor.encode();
        PickCursor parsed = PickCursor.parse(encoded);
        assertThat(parsed.pickId()).isEqualTo(pickId);
        assertThat(parsed.official()).isEqualTo(isOfficial);
        assertThatThrownBy(() -> PickCursor.parse("F_abc"))
                .isInstanceOf(BusinessException.class);

        // true인 경우도 확인
        assertThat(new PickCursor(true, 88L).encode()).isEqualTo("T_88");
    }

}
