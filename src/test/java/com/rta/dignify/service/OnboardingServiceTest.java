package com.rta.dignify.service;

import com.rta.dignify.domain.OnboardingCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// 뽑기에서 조용히 틀릴 수 있는 것만 본다. 셋 다 예외 없이 "그냥 이상한 온보딩"이 되는 부류다.
class OnboardingServiceTest {

    @Test
    @DisplayName("축마다 HIGH 한 곡 + LOW 한 곡으로 쌍이 만들어진다")
    void 축별_한_쌍() {
        List<OnboardingCandidate> candidates = List.of(
                candidate("vocal", "HIGH"), candidate("vocal", "LOW"),
                candidate("arousal", "HIGH"), candidate("arousal", "LOW"));

        List<List<OnboardingCandidate>> pairs = OnboardingService.pickPairs(candidates, new Random(1));

        assertThat(pairs).hasSize(2);
        assertThat(pairs).allSatisfy(pair -> {
            assertThat(pair).hasSize(2);
            assertThat(pair).extracting(OnboardingCandidate::getPole).containsExactlyInAnyOrder("HIGH", "LOW");
            // 한 라운드의 두 곡은 같은 축이어야 한다. 축이 섞이면 무엇을 비교시키는지가 사라진다.
            assertThat(pair.get(0).getAxis()).isEqualTo(pair.get(1).getAxis());
        });
        // 축 순서는 이름순 고정 — 랜덤이면 "몇 라운드에서 이탈했나"를 축별로 못 읽는다.
        assertThat(pairs).extracting(pair -> pair.get(0).getAxis()).containsExactly("arousal", "vocal");
    }

    @Test
    @DisplayName("한쪽 극단이 없는 축은 라운드를 만들지 않는다")
    void 반쪽_축_제외() {
        // 후보가 비활성화되면 실제로 이렇게 된다 — 비교할 반대쪽이 없는 화면이 뜨면 안 된다.
        List<OnboardingCandidate> candidates = List.of(
                candidate("vocal", "HIGH"), candidate("vocal", "LOW"),
                candidate("acoustic", "HIGH"));

        List<List<OnboardingCandidate>> pairs = OnboardingService.pickPairs(candidates, new Random(1));

        assertThat(pairs).hasSize(1);
        assertThat(pairs.get(0).get(0).getAxis()).isEqualTo("vocal");
        // 후보가 통째로 없으면 빈 목록. 앱은 이걸 받으면 라운드를 건너뛰고 피드로 간다.
        assertThat(OnboardingService.pickPairs(List.of(), new Random(1))).isEmpty();
    }

    @Test
    @DisplayName("쌍의 순서가 섞인다 — A가 항상 HIGH면 위치로 답이 굳는다")
    void 쌍_순서_셔플() {
        List<OnboardingCandidate> candidates = List.of(candidate("vocal", "HIGH"), candidate("vocal", "LOW"));

        List<String> firstPoles = IntStream.range(0, 50)
                .mapToObj(seed -> OnboardingService.pickPairs(candidates, new Random(seed)).get(0).get(0).getPole())
                .distinct().toList();

        assertThat(firstPoles).containsExactlyInAnyOrder("HIGH", "LOW");
    }

    @Test
    @DisplayName("같은 극단에 여러 곡이 있으면 그 안에서 골라 온다")
    void 극단_풀에서_랜덤() {
        List<OnboardingCandidate> highs = List.of(candidate("vocal", "HIGH"), candidate("vocal", "HIGH"),
                candidate("vocal", "HIGH"));
        List<OnboardingCandidate> candidates = new java.util.ArrayList<>(highs);
        candidates.add(candidate("vocal", "LOW"));

        for (int seed = 0; seed < 50; seed++) {
            List<OnboardingCandidate> pair = OnboardingService.pickPairs(candidates, new Random(seed)).get(0);
            OnboardingCandidate high = pair.stream().filter(c -> c.getPole().equals("HIGH")).findFirst().orElseThrow();
            assertThat(highs).contains(high);
        }
    }

    /// track은 안 본다 — 뽑기는 축·극단만으로 결정되고, 곡 정보는 그 뒤에 붙는다.
    private static OnboardingCandidate candidate(String axis, String pole) {
        return OnboardingCandidate.create(null, axis, pole);
    }
}
