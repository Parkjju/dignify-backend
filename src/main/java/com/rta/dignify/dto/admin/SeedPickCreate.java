package com.rta.dignify.dto.admin;

import java.util.List;

/// trackIds 순서 = 재생 순서. reactions는 기존 시드 계정이 누를 🔥 수(모자라면 있는 만큼).
public record SeedPickCreate(String nickname, String title, List<Long> trackIds, int reactions) {
}
