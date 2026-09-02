package com.rta.dignify.service;

import com.rta.dignify.domain.OnboardingCandidate;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.dto.onboarding.OnboardingCandidatesResponse;
import com.rta.dignify.dto.onboarding.OnboardingCandidatesResponse.Round;
import com.rta.dignify.dto.onboarding.OnboardingSeedPoolResponse;
import com.rta.dignify.repository.OnboardingCandidateRepository;
import com.rta.dignify.repository.OnboardingSeedPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/// 온보딩 소리 2지선다의 후보를 축마다 한 쌍씩 만든다.
///
/// 뽑기가 전부라 로직은 짧지만, 조용히 틀리면 온보딩이 의미를 잃는 자리가 둘 있다.
/// **극단이 한쪽만 있는 축은 라운드를 만들지 않는다**(비교가 성립하지 않는다). 그리고
/// **쌍의 순서를 섞는다** — A가 항상 HIGH면 몇 라운드 만에 위치로 답이 굳어서, 유저가 소리가 아니라
/// 자리를 고르기 시작한다.
@RequiredArgsConstructor
@Service
public class OnboardingService {
    static final String HIGH = "HIGH";
    static final String LOW = "LOW";

    private final OnboardingCandidateRepository onboardingCandidateRepository;
    private final OnboardingSeedPoolRepository onboardingSeedPoolRepository;

    /// 캐시하지 않는다. 극단마다 여러 곡을 두고 매번 랜덤으로 뽑는 게 이 기능의 전부라
    /// (고정 6곡이면 조합이 8개뿐이라 초기 유저 피드가 전부 비슷해진다) 요청마다 새로 뽑아야 한다.
    /// 후보가 스무 행 남짓이라 매 요청 전체 조회로 충분하다.
    @Transactional(readOnly = true)
    public OnboardingCandidatesResponse getCandidates() {
        List<List<OnboardingCandidate>> pairs = pickPairs(onboardingCandidateRepository.findAllActive(),
                ThreadLocalRandom.current());
        List<Round> rounds = pairs.stream()
                .map(pair -> new Round(pair.get(0).getAxis(), highTrackId(pair),
                        pair.stream().map(c -> FeedItem.from(c.getTrack(), false)).toList()))
                .toList();
        return new OnboardingCandidatesResponse(rounds);
    }

    /// 온보딩 곡 고르기 화면의 후보 목록. 심어 둔 순서 그대로 내보낸다.
    ///
    /// 캐시하지 않는다. 수십 행짜리 정적 조회라 캐시가 버는 게 없고, 곡을 갈아 끼운 뒤
    /// "언제 반영되나"를 따지게 만드는 값이 더 비싸다.
    @Transactional(readOnly = true)
    public OnboardingSeedPoolResponse getSeedPool() {
        return new OnboardingSeedPoolResponse(onboardingSeedPoolRepository.findAllActiveOrdered().stream()
                .map(s -> FeedItem.from(s.getTrack(), false))
                .toList());
    }

    /// 쌍에서 HIGH 쪽 트랙 id. `pickPairs`가 두 극단을 하나씩 넣으므로 항상 하나 있다.
    private static Long highTrackId(List<OnboardingCandidate> pair) {
        return pair.stream().filter(c -> HIGH.equals(c.getPole())).findFirst()
                .map(c -> c.getTrack().getId()).orElse(null);
    }

    /// 축마다 HIGH·LOW에서 한 곡씩 뽑아 섞은 쌍. 축 순서는 이름순으로 고정한다 —
    /// 순서까지 랜덤이면 이탈 지점을 라운드 번호로 읽을 때 축이 뒤섞여 비교가 안 된다.
    ///
    /// 인자로 `Random`을 받는 건 테스트 때문이다. 랜덤이 들어간 선택은 씨앗을 고정하지 않으면
    /// 검증할 수가 없다.
    static List<List<OnboardingCandidate>> pickPairs(List<OnboardingCandidate> candidates, Random random) {
        Map<String, Map<String, List<OnboardingCandidate>>> byAxis = candidates.stream()
                .collect(Collectors.groupingBy(OnboardingCandidate::getAxis, TreeMap::new,
                        Collectors.groupingBy(OnboardingCandidate::getPole)));

        List<List<OnboardingCandidate>> pairs = new ArrayList<>();
        for (Map<String, List<OnboardingCandidate>> poles : byAxis.values()) {
            List<OnboardingCandidate> high = poles.getOrDefault(HIGH, List.of());
            List<OnboardingCandidate> low = poles.getOrDefault(LOW, List.of());
            // 한쪽 극단이 비면(전부 비활성화됐거나 시드가 덜 들어갔거나) 그 축은 통째로 뺀다.
            if (high.isEmpty() || low.isEmpty()) {
                continue;
            }
            List<OnboardingCandidate> pair = new ArrayList<>(
                    List.of(high.get(random.nextInt(high.size())), low.get(random.nextInt(low.size()))));
            Collections.shuffle(pair, random);
            pairs.add(pair);
        }
        return pairs;
    }
}
