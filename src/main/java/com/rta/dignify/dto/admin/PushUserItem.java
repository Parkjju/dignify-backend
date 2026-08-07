package com.rta.dignify.dto.admin;

import java.util.List;

/// 푸시 대상 후보. 토큰이 등록된 유저만 나온다 — 없으면 쏴봐야 안 간다.
public record PushUserItem(Long userId, String nickname, int devices, List<Integer> builds, List<String> timeZones) {
}
