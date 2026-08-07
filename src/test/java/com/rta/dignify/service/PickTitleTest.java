package com.rta.dignify.service;

import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// 스프링 컨텍스트도 DB도 필요 없다 — 검증이 static 메서드라 순수 단위 테스트다.
public class PickTitleTest {

    @Test
    @DisplayName("제목 없음은 null 하나로 모인다 - null / 빈 문자열 / 공백만")
    void blankTitleBecomesNull() {
        assertThat(PickService.validatedTitle(null)).isNull();
        assertThat(PickService.validatedTitle("")).isNull();
        assertThat(PickService.validatedTitle("   \n ")).isNull();
    }

    @Test
    @DisplayName("정상 제목은 trim만 되어 그대로 통과한다")
    void normalTitlePassesTrimmed() {
        assertThat(PickService.validatedTitle("  여름밤 드라이브  ")).isEqualTo("여름밤 드라이브");
    }

    @ParameterizedTest
    @DisplayName("금칙어는 공백·특수문자를 끼워넣어도 걸린다")
    @ValueSource(strings = {"병신", "이 노래 진짜 병신같음", "ㅅ ㅂ 개좋음", "f.u.c.k it", "FUCK"})
    void profanityIsBlocked(String title) {
        assertThatThrownBy(() -> PickService.validatedTitle(title))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PICK_TITLE_BLOCKED);
    }

    @ParameterizedTest
    @DisplayName("멀쩡한 말이 부분 문자열로 걸리지 않는다 - 목록에서 뺀 단어들의 회귀 방지")
    @ValueSource(strings = {
            "아직 보지 못한 여름",   // 보지
            "자지 않는 밤에",        // 자지
            "강아지 새끼처럼",       // 새끼
            "2024년의 마지막",       // 년
            "미친 듯이 좋은 곡",     // 미친
            "불이 꺼져 있어도",      // 꺼져
            "Classic Bass Lines",   // ass
            "Analog Nights",        // anal
            "Grapefruit"            // rape
    })
    void legitTitlesAreNotBlocked(String title) {
        assertThat(PickService.validatedTitle(title)).isEqualTo(title);
    }

    @Test
    @DisplayName("차단 문구는 로케일별로 다르다 - iOS가 이 message를 그대로 띄운다")
    void blockedMessageIsLocalized() {
        try {
            LocaleContextHolder.setLocale(Locale.KOREAN);
            assertThat(catchMessage("병신")).isEqualTo("쓸 수 없는 표현이 있어요. 제목을 바꿔주세요.");

            LocaleContextHolder.setLocale(Locale.ENGLISH);
            assertThat(catchMessage("병신")).isEqualTo("That title contains a word we can't allow. Try another one.");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    private String catchMessage(String title) {
        try {
            PickService.validatedTitle(title);
            throw new AssertionError("금칙어인데 통과했다: " + title);
        } catch (BusinessException e) {
            return e.getMessage();
        }
    }
}
