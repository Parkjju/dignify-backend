package com.rta.dignify.dto.auth;

/**
 * Google ID 토큰에서 받는 값들
 *
 * @param email         유저 이메일. Apple과 달리 재로그인에도 항상 들어온다.
 * @param googleId      Google 고유 ID(sub). 유저가 이메일을 바꿔도 안 변한다.
 * @param emailVerified 이메일 소유 검증 여부. 기존 계정에 연결할지를 이 값으로 가른다 —
 *                      검증 안 된 이메일로 연결을 허용하면 남의 계정을 가져갈 수 있다.
 */
public record GoogleIdentity(String email, String googleId, boolean emailVerified) {
}
