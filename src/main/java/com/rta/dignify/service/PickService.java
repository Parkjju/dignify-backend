package com.rta.dignify.service;

import com.rta.dignify.domain.*;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.dto.feed.FeedResponse;
import com.rta.dignify.dto.pick.*;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.global.util.ProfanityFilter;
import com.rta.dignify.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
public class PickService {

    static final int PAGE_SIZE = 20;
    static final Set<String> ALLOWED_EMOJIS = Set.of("🔥", "🫶", "🥲", "🪩", "👀");
    /// 100 이후는 안 보낸다 — 이 규모에서 도달할 일이 없고, 도달하면 그때 정한다.
    static final Set<Long> REACTION_MILESTONES = Set.of(1L, 5L, 10L, 20L, 50L, 100L);

    private final PickReactionRepository pickReactionRepository;
    private final PickTrackRepository pickTrackRepository;
    private final PickRepository pickRepository;
    private final UserHypeTrackRepository userHypeTrackRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    /// apns.enabled=false(테스트/로컬)면 PushService 빈이 없다. 직접 주입하면 컨텍스트가 안 뜬다.
    /// ArtistRequestService와 같은 패턴.
    private final ObjectProvider<PushService> pushService;

    @Transactional(readOnly = true)
    public PickListResponse getPicks(Long userId, String cursorString, boolean mine) {
        PickCursor cursor = cursorString == null ? null : PickCursor.parse(cursorString);
        Boolean curOfficial = cursor == null ? null : cursor.official();
        Long curPickId = cursor == null ? null : cursor.pickId();

        List<Pick> pickList = pickRepository.findPage(curOfficial, curPickId, mine ? userId : null, PageRequest.of(0, PickService.PAGE_SIZE + 1));
        boolean hasMore = pickList.size() > PAGE_SIZE;
        if (hasMore) pickList = pickList.subList(0, PAGE_SIZE);
        if (pickList.isEmpty()) return new PickListResponse(List.of(), null, false);

        List<Long> pickIds = pickList.stream().map(Pick::getId).toList();

        Map<Long, List<PickTrack>> tracksByPick = pickTrackRepository.findPickTracksByPickIds(pickIds)
                .stream()
                .collect(Collectors.groupingBy(pt -> pt.getPick().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, Map<String, Long>> reactionsByPick = pickReactionRepository.countPickReactionsByPickIds(pickIds)
                .stream()
                .collect(Collectors.groupingBy(PickReactionCount::pickId, Collectors.toMap(PickReactionCount::emoji, PickReactionCount::count)));
        Map<Long, String> myReactionByPick = userId == null ? Map.of() : pickReactionRepository.findPickReactionsByUserIdInPickIds(pickIds, userId).stream()
                .collect(Collectors.toMap(pr -> pr.getPick().getId(), PickReaction::getEmoji));

        List<PickResponse> items = pickList.stream()
                .filter(p -> !tracksByPick.getOrDefault(p.getId(), List.of()).isEmpty())
                .map(p -> PickResponse.of(p, tracksByPick.get(p.getId()), reactionsByPick.getOrDefault(p.getId(), Map.of()), myReactionByPick.get(p.getId()), userId))
                .toList();
        Pick last = pickList.getLast();

        return new PickListResponse(items, hasMore ? new PickCursor(last.getIsOfficial(), last.getId()).encode() : null, hasMore);
    }

    @Transactional(readOnly = true)
    public FeedResponse getPickDetail(Long userId, Long pickId) {
        Pick pick = pickRepository.findById(pickId).orElseThrow(() -> new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST));
        if (pick.getIsDeleted()) {
            throw new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST);
        }

        List<PickTrack> pickTracks = pickTrackRepository.findPickTracksByPickIds(List.of(pickId));
        if (pickTracks.isEmpty()) {
            throw new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST);
        }

        List<Long> trackIdsInPick = pickTracks.stream().map(pt -> pt.getTrack().getId()).toList();
        Set<Long> hypedTrackIdsInPick = userId == null ? Set.of() : userHypeTrackRepository.findHypedTrackIds(userId, trackIdsInPick);
        List<FeedItem> feedItems = pickTracks
                .stream()
                .filter(pt -> pt.getTrack().getIsActive())
                .map(pt -> FeedItem.from(pt.getTrack(), hypedTrackIdsInPick.contains(pt.getTrack().getId())))
                .toList();
        return new FeedResponse(feedItems, null, null, null);
    }

    @Transactional
    public void createPick(Long userId, PickCreate request) {
        List<Long> trackIds = request.trackIds();
        Map<Long, Track> trackById = trackRepository.findAllById(trackIds).stream()
                .collect(Collectors.toMap(Track::getId, t -> t));
        if (trackById.size() != request.trackIds().size()) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }

        String title = validatedTitle(request.title());
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pick newPick = pickRepository.save(Pick.create(user, title, false));

        List<PickTrack> pickTracks = IntStream.range(0, trackIds.size())
                .mapToObj(i -> PickTrack.create(newPick, trackById.get(trackIds.get(i)), i))
                .toList();
        pickTrackRepository.saveAll(pickTracks);
    }

    @Transactional
    public void deletePick(Long userId, Long pickId) {
        Pick pick = pickRepository.findById(pickId).orElseThrow(() -> new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST));
        if (pick.getIsDeleted() || !pick.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST);
        }
        pick.delete();
    }

    @Transactional
    public void setReaction(Long userId, Long pickId, PickReactionRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pick pick = pickRepository.findById(pickId).orElseThrow(() -> new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST));
        String emoji = request.emoji();
        if (pick.getIsDeleted()) {
            throw new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST);
        }
        if (!ALLOWED_EMOJIS.contains(request.emoji())) {
            throw new BusinessException(ErrorCode.INVALID_EMOJI);
        }
        pickReactionRepository.findByPickIdAndUserId(pickId, userId)
                        .ifPresentOrElse(r -> r.changeEmoji(emoji), () -> pickReactionRepository.save(PickReaction.create(pick, user, emoji)));
        notifyIfMilestone(pick, user);
    }

    /// 반응이 마일스톤에 닿았으면 픽 주인에게 푸시한다(§10.5). 반응마다 보내지 않는다.
    ///
    /// `count > maxNotifiedReactions`가 **이모지 교체와 재요청에서 재발송을 막는다** — 둘 다
    /// 카운트가 안 변하고, 직전 발송에서 `max`가 이미 그 값으로 올라가 있다.
    /// 해제(`deleteReaction`)는 여기를 안 부른다.
    private void notifyIfMilestone(Pick pick, User reactor) {
        Long ownerId = pick.getUser().getId();
        // COUNT는 JPQL이라 auto-flush가 걸려 방금 넣은 반응이 보인다.
        long count = pickReactionRepository.countByPickIdExcludingOwner(pick.getId(), ownerId);
        if (count <= pick.getMaxNotifiedReactions() || !REACTION_MILESTONES.contains(count)) {
            return;
        }
        pick.markNotified((int) count);
        // 발송 실패는 whenComplete 콜백에서 끝나 여기로 안 올라온다 — 반응 저장이 푸시 때문에
        // 롤백되지 않는다. apns.enabled=false(테스트/로컬)면 빈이 없어 no-op다.
        pushService.ifAvailable(p -> p.sendPickReaction(ownerId, reactor.getNickname(), count));
    }

    @Transactional
    public void deleteReaction(Long userId, Long pickId) {
        pickReactionRepository.findByPickIdAndUserId(pickId, userId)
                .ifPresent(pickReactionRepository::delete);
    }

    @Transactional
    public void updateTitle(Long userId, Long pickId, PickTitleUpdate request) {
        Pick pick = pickRepository.findById(pickId).orElseThrow(() -> new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST));
        if (pick.getIsDeleted() || !pick.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST);
        }
        pick.changeTitle(validatedTitle(request.title()));
    }

    /**
     * 픽 제목을 저장 가능한 형태로 만든다. {@code POST /picks}와 {@code PUT /picks/{id}/title}이 함께 쓴다 —
     * 갈라 쓰면 게시에서 막히는 문구가 수정으로는 통과하는 구멍이 생긴다.
     *
     * @param title 클라가 보낸 원본 제목 (null 허용)
     * @return trim한 제목, 또는 제목 없음을 뜻하는 null
     * @throws BusinessException 금칙어가 걸리면 400 PICK_TITLE_BLOCKED
     */
    static String validatedTitle(String title) {
        // trim 후 빈 문자열은 NULL로 접는다 — 빈 문자열과 NULL이 둘 다 "제목 없음"이면
        // 클라의 폴백 제목 판정이 두 갈래로 갈린다.
        if (title == null) return null;
        String trimmed = title.trim();
        if (trimmed.isEmpty()) return null;

        if (ProfanityFilter.contains(trimmed)) {
            // 어떤 단어가 걸렸는지는 말하지 않는다 — 우회 가이드가 된다.
            // 이 message는 iOS가 그대로 화면에 띄우는 유일한 서버 문구라 로케일 분기가 필수다
            // (PickComposeView.swift:483). 다른 에러는 클라가 자체 문구로 갈아끼운다.
            throw new BusinessException(ErrorCode.PICK_TITLE_BLOCKED,
                    "ko".equals(LocaleContextHolder.getLocale().getLanguage())
                            ? "쓸 수 없는 표현이 있어요. 제목을 바꿔주세요."
                            : "That title contains a word we can't allow. Try another one.");
        }
        return trimmed;
    }
}
