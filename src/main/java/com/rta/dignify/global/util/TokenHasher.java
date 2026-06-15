package com.rta.dignify.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TokenHasher {

    /**
     * 토큰을 SHA-256으로 해싱해 64자 16진수 문자열로 반환한다.
     * <p>
     * 1. SHA-256 MessageDigest 인스턴스 획득
     * 2. 토큰 문자열을 UTF-8 바이트 배열로 변환
     * 3. 바이트 배열의 SHA-256 해시값(32바이트) 계산
     * 4. 각 바이트를 2자리 16진수로 변환해 이어붙임 (32 * 2 = 64자)
     */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JVM 표준 알고리즘이라 실질적으로 발생 불가능한 예외.
            // 발생 시 unchecked exception으로 던져 호출부 트랜잭션을 롤백시키고,
            // BusinessException이 아니므로 GlobalExceptionHandler의
            // handleInternalServerException(500)으로 처리됨.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
