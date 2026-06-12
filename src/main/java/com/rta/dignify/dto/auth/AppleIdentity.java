package com.rta.dignify.dto.auth;

/**
 * identity 토큰에서 받는 값들
 * @param email 유저 이메일
 * @param sub Apple 고유 ID
 */
public record AppleIdentity(String email, String sub) {
}
