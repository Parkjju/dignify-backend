package com.rta.dignify.global.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/// 닉네임/픽 제목이 함께 쓰는 금칙어 검사. 목록은 코드가 아니라
/// {@code resources/moderation/blocked-words.txt}에 있다 — 재배포만으로 갱신 가능하게 하기 위해서다.
///
/// 공백·특수문자를 걷어내 `ㅅ ㅂ` / `f.u.c.k` 수준의 끼워넣기만 무력화한다.
/// **자모 분해도 leet 치환도 하지 않는다** — 정교해질수록 오탐이 늘고, 여기선 오탐이 미탐보다 나쁘다.
public final class ProfanityFilter {

    /// 호환 자모(ㄱ-ㅎㅏ-ㅣ)를 남기는 게 핵심이다 — 지우면 `ㅅㅂ`가 빈 문자열이 돼 통과한다.
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^0-9a-z가-힣ㄱ-ㅎㅏ-ㅣ]");
    private static final String BLOCKLIST_RESOURCE = "moderation/blocked-words.txt";
    private static final Set<String> BLOCKED_WORDS = load();

    private ProfanityFilter() {
    }

    public static boolean contains(String text) {
        String normalized = NOT_LETTER_OR_DIGIT.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
        return BLOCKED_WORDS.stream().anyMatch(normalized::contains);
    }

    private static Set<String> load() {
        Set<String> words = new HashSet<>();
        try (InputStream in = ProfanityFilter.class.getClassLoader().getResourceAsStream(BLOCKLIST_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("금칙어 목록 파일을 찾을 수 없습니다: " + BLOCKLIST_RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                words.add(trimmed);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("금칙어 목록을 불러오지 못했습니다: " + BLOCKLIST_RESOURCE, e);
        }
        return Set.copyOf(words);
    }
}
