package com.rta.dignify.dto.admin;

import java.util.List;

/// 푸시 대상 현황. 유저별 목록과 빌드별 대수를 같이 준다 — MIN_BUILD를 걸기 전에 몇 대가 남는지
/// 알아야 하는데, 유저 행만 봐서는 기기 수를 사람이 세어야 한다.
public record PushTargets(List<PushUserItem> users, List<BuildStat> builds) {

    /// build가 null인 행은 앱을 한 번도 안 켜서 빌드가 안 잡힌 기기다. MIN_BUILD를 걸면 전부 빠진다.
    public record BuildStat(Integer build, int devices) {
    }
}
