package com.rta.dignify.dto.admin;

/// 장르별 활성 트랙 수. 어느 장르가 얇은지 보려는 것이라 노출 이름(genres.genre_name_en) 기준이다.
public record GenreStat(String genre, long tracks) {
}
