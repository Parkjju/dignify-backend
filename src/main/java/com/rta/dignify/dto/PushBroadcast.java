package com.rta.dignify.dto;

import jakarta.validation.constraints.NotBlank;

/// 운영자 공지 푸시. force=true면 기기 로컬 시각이 새벽이어도 보낸다.
/// userId가 있으면 그 유저 기기에만 간다 — 전체 발송 전 본인 기기로 찍어보는 용도.
public record PushBroadcast(@NotBlank String title, @NotBlank String body, boolean force, Long userId) {}
