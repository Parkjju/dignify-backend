package com.rta.dignify.dto.admin;

import java.time.Instant;

/// 대기중인 아티스트 등록 요청 한 건. trackCount는 이름이 겹치는 활성 트랙 수 —
/// 이미 수집돼 있는지 사람이 눈으로 판단하라고 같이 보낸다(표기가 조금만 달라도 0이 나온다).
public record ArtistRequestItem(Long id, String artistName, String nickname, Instant createdAt, long trackCount) {
}
