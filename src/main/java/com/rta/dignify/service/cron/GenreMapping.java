package com.rta.dignify.service.cron;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

// iTunes primaryGenreName을 우리가 노출하는 13개 장르로 접는다.
//
// genres 테이블에는 iTunes 분류 420종이 통째로 시드돼 있어서, 원본 이름으로 조회하면
// 'Singer/Songwriter' 같은 리프도 전부 조회에 성공한다. 그대로 두면 트랙이 리프에 꽂히고
// ① 온보딩 목록이 길어지고 ② 유저가 'Jazz'를 골라도 'Vocal Jazz' 트랙이 선호 장르에 안 잡히며
// ③ Digging Profile의 '폭' 축이 top 장르 점유율이라 다들 폭넓은 유형으로 밀린다.
// 2026-08-05에 리프 27개를 13개로 병합했고, 이 클래스가 그게 다시 쪼개지는 걸 막는다.
//
// DB 컬럼이나 어드민 API를 두지 않는 이유는 금칙어 목록과 같다 — 갱신이 상수 한 줄 + main 푸시
// (자동배포)라 비용이 거의 0이고, 매핑은 코드와 같은 속도로 바뀌는 값이다.
public final class GenreMapping {
    private GenreMapping() {
    }

    // 노출하는 13개. genres.genre_name_en과 문자열이 정확히 일치해야 한다.
    private static final Set<String> CANONICAL = Set.of(
            "Rock", "Hip-Hop/Rap", "Pop", "Jazz", "Dance", "Country", "R&B/Soul",
            "K-Pop", "Electronic", "CCM", "Latin", "Alternative", "Soundtrack"
    );

    // 2026-08-05 병합분. 전부 실제로 트랙이 들어온 적 있는 값이다.
    // 목록을 iTunes 420종으로 미리 채우지 않는다 — 어떤 게 실제로 오는지는 아래 log.warn이 알려준다.
    private static final Map<String, String> ALIASES = Map.ofEntries(
            entry("Bass", "Electronic"),
            entry("Dubstep", "Electronic"),
            entry("House", "Electronic"),
            entry("Jungle/Drum'n'bass", "Electronic"),
            entry("Electronica", "Electronic"),
            entry("New Age", "Electronic"),
            entry("Downtempo", "Electronic"),
            entry("Techno", "Electronic"),
            entry("Trance", "Electronic"),
            entry("Garage", "Electronic"),

            entry("Blues", "Rock"),
            entry("Metal", "Rock"),
            entry("Korean Rock", "Rock"),

            entry("Singer/Songwriter", "Alternative"),
            entry("Indie Pop", "Alternative"),
            entry("Indie Rock", "Alternative"),

            entry("J-Pop", "Pop"),
            entry("Christmas", "Pop"),
            entry("Holiday", "Pop"),
            entry("Adult Contemporary", "Pop"),

            // iTunes는 같은 아티스트 안에서도 'CCM'과 'Christian'을 섞어 준다. 우리 장르 이름이
            // CCM이라 Christian이 그대로 드롭되고 있었다 — Red Rocks Worship 200곡 중 194곡.
            entry("Christian", "CCM"),

            entry("Rap", "Hip-Hop/Rap"),
            entry("Hip-Hop", "Hip-Hop/Rap"),

            entry("Disco", "Dance"),
            entry("African Dancehall", "Dance"),

            entry("Funk", "R&B/Soul"),
            entry("Korean Indie", "K-Pop"),
            entry("Folk", "Country"),
            entry("TV Soundtrack", "Soundtrack")
    );

    // 13개 중 하나로 접은 이름. 매핑이 없으면 null — 호출부가 그 트랙을 버린다.
    // 폴백 통을 두지 않는 이유: 장르가 틀리게 붙으면 개인화가 조용히 나빠지는데, 드롭은 로그로 보인다.
    public static String canonical(String itunesGenreName) {
        if (itunesGenreName == null) {
            return null;
        }
        if (CANONICAL.contains(itunesGenreName)) {
            return itunesGenreName;
        }
        return ALIASES.get(itunesGenreName);
    }
}
