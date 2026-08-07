package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Pick;
import com.rta.dignify.domain.PickReaction;
import com.rta.dignify.domain.PickTrack;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.dto.pick.PickCreate;
import com.rta.dignify.dto.pick.PickReactionCount;
import com.rta.dignify.dto.pick.PickReactionRequest;
import com.rta.dignify.dto.pick.PickResponse;
import com.rta.dignify.dto.pick.PickTitleUpdate;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.PickReactionRepository;
import com.rta.dignify.repository.PickRepository;
import com.rta.dignify.repository.PickTrackRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// ⚠️ 각 테스트에서 **성공하는 서비스 호출을 먼저 하고 예외 케이스를 마지막에 몰아둔다.**
/// 서비스의 `@Transactional`이 테스트 트랜잭션에 참여하는 구조라, 안쪽에서 예외가 나면
/// 트랜잭션이 rollback-only로 찍힌다. 그 뒤에 성공하는 호출이 커밋을 시도하면
/// `UnexpectedRollbackException`이 난다 — 검증하려던 로직과 무관한 실패라 순서로 피한다.
@SpringBootTest
@Transactional
public class PickServiceTest {

    @Autowired GenreRepository genreRepository;
    @Autowired UserRepository userRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired PickRepository pickRepository;
    @Autowired PickTrackRepository pickTrackRepository;
    @Autowired PickReactionRepository pickReactionRepository;
    @Autowired PickService pickService;

    User owner;
    User other;
    List<Track> tracks;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.create("owner@dignify.app", "owner"));
        other = userRepository.save(User.create("other@dignify.app", "other"));
        Genre genre = genreRepository.save(Genre.create("Rock", "락"));

        tracks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tracks.add(trackRepository.save(Track.create(
                    "ext-" + i, "Artist " + i, "Album " + i, "Track " + i,
                    "https://example.com/preview" + i + ".m4a", "https://example.com/track" + i,
                    "https://example.com/art" + i + ".jpg", Instant.now(), genre, "KR", "ITUNES")));
        }
    }

    @Test
    @DisplayName("담은 순서 그대로 position이 박힌다 - findAllById 반환 순서에 안 휘둘린다")
    void trackOrderFollowsRequestNotFetchOrder() {
        // 2 → 0 → 1. id 오름차순과 일부러 다르게 보낸다.
        pickService.createPick(owner.getId(), new PickCreate("  여름밤 드라이브  ", trackIds(2, 0, 1)));
        Pick pick = lastPick();

        assertThat(pick.getTitle()).isEqualTo("여름밤 드라이브");   // 양끝 공백은 trim
        assertThat(pick.getIsOfficial()).isFalse();                 // 씰은 이 경로로 못 붙는다
        assertThat(pickTrackRepository.findPickTracksByPickIds(List.of(pick.getId())))
                .extracting(pt -> pt.getTrack().getId())
                .containsExactly(tracks.get(2).getId(), tracks.get(0).getId(), tracks.get(1).getId());
        assertThat(pickTrackRepository.findPickTracksByPickIds(List.of(pick.getId())))
                .extracting(PickTrack::getPosition)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("공백만 있는 제목은 NULL로 저장된다 - 빈 문자열과 갈라지면 클라 폴백이 두 갈래가 된다")
    void blankTitleIsStoredAsNull() {
        pickService.createPick(owner.getId(), new PickCreate("   \n ", trackIds(0)));
        assertThat(lastPick().getTitle()).isNull();

        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(0)));
        assertThat(lastPick().getTitle()).isNull();
    }

    @Test
    @DisplayName("없는 트랙 · 중복 트랙은 저장 전에 걸린다")
    void createRejectsMissingAndDuplicateTracks() {
        assertThatThrownBy(() -> pickService.createPick(owner.getId(),
                new PickCreate(null, List.of(tracks.getFirst().getId(), 999_999L))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRACK_NOT_FOUND);

        // 중복은 조회 결과가 요청보다 짧아져 같은 검사에 걸린다 (별도 검사를 두지 않은 근거)
        Long duplicated = tracks.getFirst().getId();
        assertThatThrownBy(() -> pickService.createPick(owner.getId(),
                new PickCreate(null, List.of(duplicated, duplicated))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRACK_NOT_FOUND);
    }

    @Test
    @DisplayName("금칙어 제목은 게시가 막힌다")
    void createRejectsBlockedTitle() {
        assertThatThrownBy(() -> pickService.createPick(owner.getId(),
                new PickCreate("이 노래 진짜 병신같음", trackIds(0))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PICK_TITLE_BLOCKED);
    }

    @Test
    @DisplayName("삭제한 픽은 목록에서 실제로 빠진다 - is_deleted 플래그만 보면 쿼리 필터를 검증 못 한다")
    void deletedPickDisappearsFromList() {
        pickService.createPick(owner.getId(), new PickCreate("지울 픽", trackIds(0)));
        Long pickId = lastPick().getId();
        assertThat(pickService.getPicks(owner.getId(), null, false).items())
                .extracting(PickResponse::pickId).containsExactly(pickId);

        pickService.deletePick(owner.getId(), pickId);

        assertThat(pickRepository.findById(pickId)).get()
                .extracting(Pick::getIsDeleted).isEqualTo(true);   // 하드 삭제가 아니다
        assertThat(pickService.getPicks(owner.getId(), null, false).items()).isEmpty();
    }

    @Test
    @DisplayName("남의 픽 · 이미 삭제된 픽 · 없는 id는 전부 같은 404 - 존재를 숨긴다")
    void deleteHidesEverythingBehindOne404() {
        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(0)));
        Long ownerPickId = lastPick().getId();
        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(1)));
        Long deletedPickId = lastPick().getId();
        pickService.deletePick(owner.getId(), deletedPickId);

        assertNotFound(() -> pickService.deletePick(other.getId(), ownerPickId));    // 남의 픽
        assertNotFound(() -> pickService.deletePick(owner.getId(), deletedPickId));  // 이미 삭제
        assertNotFound(() -> pickService.deletePick(owner.getId(), 999_999L));       // 없는 id
    }

    @Test
    @DisplayName("반응은 PUT 하나로 upsert - 이모지를 바꿔도 행이 안 늘어난다")
    void reactionIsUpsertedNotAppended() {
        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(0)));
        Long pickId = lastPick().getId();

        pickService.setReaction(other.getId(), pickId, new PickReactionRequest("🔥"));
        pickService.setReaction(other.getId(), pickId, new PickReactionRequest("🫶"));
        // 같은 이모지를 다시 보내도 멱등이어야 한다 (연타 · 재시도)
        pickService.setReaction(other.getId(), pickId, new PickReactionRequest("🫶"));

        Optional<PickReaction> reaction = pickReactionRepository.findByPickIdAndUserId(pickId, other.getId());
        assertThat(reaction).isPresent();
        assertThat(reaction.get().getEmoji()).isEqualTo("🫶");

        List<PickReactionCount> counts = pickReactionRepository.countPickReactionsByPickIds(List.of(pickId));
        assertThat(counts).singleElement()
                .satisfies(c -> {
                    assertThat(c.emoji()).isEqualTo("🫶");
                    assertThat(c.count()).isEqualTo(1L);   // uq_pick_user - 한 유저 한 행
                });
    }

    @Test
    @DisplayName("반응 해제는 없는 것도 조용히 성공한다 - 404면 iOS 낙관적 업데이트가 되살아난다")
    void deleteReactionIsIdempotent() {
        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(0)));
        Long pickId = lastPick().getId();
        pickService.setReaction(other.getId(), pickId, new PickReactionRequest("🔥"));

        assertThatCode(() -> pickService.deleteReaction(other.getId(), pickId)).doesNotThrowAnyException();
        assertThatCode(() -> pickService.deleteReaction(other.getId(), pickId)).doesNotThrowAnyException();

        assertThat(pickReactionRepository.findByPickIdAndUserId(pickId, other.getId())).isEmpty();
        assertThat(pickReactionRepository.countPickReactionsByPickIds(List.of(pickId))).isEmpty();
    }

    @Test
    @DisplayName("화이트리스트 밖 이모지 · 삭제된 픽에는 반응이 안 붙는다")
    void reactionRejectsUnknownEmojiAndDeletedPick() {
        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(0)));
        Long alivePickId = lastPick().getId();
        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(1)));
        Long deletedPickId = lastPick().getId();
        pickService.deletePick(owner.getId(), deletedPickId);

        assertThatThrownBy(() -> pickService.setReaction(other.getId(), alivePickId, new PickReactionRequest("💩")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_EMOJI);
        // 서로게이트 페어 반쪽 - 문자열 contains로 검사하면 통과하던 값
        assertThatThrownBy(() -> pickService.setReaction(other.getId(), alivePickId, new PickReactionRequest("\uD83D")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_EMOJI);
        assertNotFound(() -> pickService.setReaction(other.getId(), deletedPickId, new PickReactionRequest("🔥")));
    }

    @Test
    @DisplayName("마일스톤 판정에서 소유자 본인 반응은 빠진다 - 자기 픽에 눌러도 첫 반응이 아니다")
    void ownReactionDoesNotCountTowardMilestone() {
        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(0)));
        Pick pick = lastPick();

        pickService.setReaction(owner.getId(), pick.getId(), new PickReactionRequest("🔥"));
        assertThat(pick.getMaxNotifiedReactions()).isZero();

        pickService.setReaction(other.getId(), pick.getId(), new PickReactionRequest("🔥"));
        assertThat(pick.getMaxNotifiedReactions()).isEqualTo(1);   // 남이 눌렀을 때가 1번째
    }

    @Test
    @DisplayName("이모지 교체 · 재요청은 마일스톤을 다시 찍지 않는다 - 같은 알림이 두 번 가면 안 된다")
    void milestoneIsNotifiedOnlyOnce() {
        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(0)));
        Pick pick = lastPick();

        pickService.setReaction(other.getId(), pick.getId(), new PickReactionRequest("🔥"));
        assertThat(pick.getMaxNotifiedReactions()).isEqualTo(1);

        // 교체 · 같은 값 재요청 · 껐다 다시 켜기 - 전부 카운트가 1로 되돌아온다
        pickService.setReaction(other.getId(), pick.getId(), new PickReactionRequest("🫶"));
        pickService.setReaction(other.getId(), pick.getId(), new PickReactionRequest("🫶"));
        pickService.deleteReaction(other.getId(), pick.getId());
        pickService.setReaction(other.getId(), pick.getId(), new PickReactionRequest("🔥"));

        assertThat(pick.getMaxNotifiedReactions()).isEqualTo(1);   // 1에서 안 움직인다
    }

    @Test
    @DisplayName("마일스톤이 아닌 카운트는 넘어간다 - 반응마다 보내지 않는다")
    void nonMilestoneCountsAreSkipped() {
        pickService.createPick(owner.getId(), new PickCreate(null, trackIds(0)));
        Pick pick = lastPick();

        // 2 · 3 · 4번째는 마일스톤이 아니라 max가 1에 머문다
        for (int i = 0; i < 4; i++) {
            User reactor = userRepository.save(User.create("r" + i + "@dignify.app", "reactor" + i));
            pickService.setReaction(reactor.getId(), pick.getId(), new PickReactionRequest("🔥"));
        }
        assertThat(pick.getMaxNotifiedReactions()).isEqualTo(1);

        User fifth = userRepository.save(User.create("r5@dignify.app", "reactor5"));
        pickService.setReaction(fifth.getId(), pick.getId(), new PickReactionRequest("🔥"));
        assertThat(pick.getMaxNotifiedReactions()).isEqualTo(5);
    }

    @Test
    @DisplayName("제목 수정도 게시와 같은 검증을 탄다 - 갈라 쓰면 수정으로 금칙어가 통과한다")
    void updateTitleSharesTheSameValidation() {
        pickService.createPick(owner.getId(), new PickCreate("처음 제목", trackIds(0)));
        Long pickId = lastPick().getId();

        pickService.updateTitle(owner.getId(), pickId, new PickTitleUpdate("  고친 제목  "));
        assertThat(lastPick().getTitle()).isEqualTo("고친 제목");

        pickService.updateTitle(owner.getId(), pickId, new PickTitleUpdate("   "));
        assertThat(lastPick().getTitle()).isNull();   // 제목 삭제

        assertThatThrownBy(() -> pickService.updateTitle(owner.getId(), pickId, new PickTitleUpdate("병신같은 곡")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PICK_TITLE_BLOCKED);
        assertNotFound(() -> pickService.updateTitle(other.getId(), pickId, new PickTitleUpdate("남이 고침")));
    }

    // MARK: helpers

    private List<Long> trackIds(int... indexes) {
        return Arrays.stream(indexes).mapToObj(i -> tracks.get(i).getId()).toList();
    }

    /// `createPick`이 void라 방금 만든 픽을 id 최대값으로 집는다.
    /// 테스트 트랜잭션이 매번 롤백돼서 이 테스트가 만든 픽만 남아 있다.
    private Pick lastPick() {
        return pickRepository.findAll().stream().max(Comparator.comparing(Pick::getId)).orElseThrow();
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PICK_DOES_NOT_EXIST);
    }
}
