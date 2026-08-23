package com.rta.dignify.controller;

import com.rta.dignify.dto.onboarding.OnboardingCandidatesResponse;
import com.rta.dignify.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/onboarding")
@RestController
public class OnboardingController {
    private final OnboardingService onboardingService;

    /// 로그인 직후 온보딩에서만 부른다. permitAll이 아니다 — 게스트는 하입을 못 하므로
    /// 라운드를 볼 이유가 없다(게스트의 첫 화면은 콜드스타트 피드가 받는다).
    @GetMapping("/candidates")
    public OnboardingCandidatesResponse getCandidates() {
        return onboardingService.getCandidates();
    }
}
