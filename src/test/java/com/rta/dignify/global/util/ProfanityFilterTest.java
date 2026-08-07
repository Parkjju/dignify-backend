package com.rta.dignify.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/// 닉네임/픽 제목이 공용으로 쓰는 필터 자체를 검사한다. 각 도메인의 null/trim 처리는
/// PickTitleTest·UserServiceTest에서 본다 — 여긴 매칭 로직만.
class ProfanityFilterTest {

    @ParameterizedTest
    @DisplayName("대소문자·공백·구두점을 끼워넣어도 걸린다")
    @ValueSource(strings = {"병신", "ㅅ ㅂ", "FUCK", "f.u.c.k"})
    void blocksProfanity(String text) {
        assertThat(ProfanityFilter.contains(text)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("금칙어의 부분 문자열일 뿐인 정상 단어는 통과한다 - Scunthorpe 문제 회귀 방지")
    @ValueSource(strings = {"Classic Bass", "Analog Nights", "Grapefruit", "여름밤 드라이브"})
    void allowsFalsePositiveProneWords(String text) {
        assertThat(ProfanityFilter.contains(text)).isFalse();
    }
}
