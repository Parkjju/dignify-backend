package com.rta.dignify.domain;

/// 재방문 푸시 2종의 발송 시각 A/B. 배정은 저장하지 않고 user_id로 계산한다.
///
/// | 그룹 | 신곡 알림 | 주간 추천 |
/// | A (짝수) | 매일 09:05 | 월 18:05 |
/// | B (홀수) | 매일 18:05 | 월 09:05 |
///
/// **테스트 기간 중 식을 바꾸지 말 것.** 바꾸면 그 전 판독과 이어지지 않는다.
public enum PushVariant {
    A, B;

    public static PushVariant of(long userId) {
        return userId % 2 == 0 ? A : B;
    }

    /// 이 그룹의 user_id % 2 값. SQL에서 그룹을 거를 때 쓴다.
    public int mod() {
        return this == A ? 0 : 1;
    }

    /// 크론이 받는 실행 슬롯. 한국 시각 09:05(am)·18:05(pm).
    public enum Slot {
        AM, PM;

        public static Slot parse(String slot) {
            return Slot.valueOf(slot.toUpperCase());
        }
    }

    public static PushVariant newReleaseGroup(Slot slot) {
        return slot == Slot.AM ? A : B;
    }

    public static PushVariant weeklyPickGroup(Slot slot) {
        return slot == Slot.AM ? B : A;
    }
}
