package com.rta.dignify.dto.admin;

/// artistId 백필 한 배치의 결과. cursor는 다음 호출에 그대로 넘길 값이고,
/// checked가 0이면 커서가 끝까지 갔다는 뜻이다 (remaining은 0이 아닐 수 있다 —
/// iTunes에서 내려가 조회가 안 되는 곡이 남는다).
public record ArtistIdBatch(int checked, int matched, long cursor, long remaining) {
}
