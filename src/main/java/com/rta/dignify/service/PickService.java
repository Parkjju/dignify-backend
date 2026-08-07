package com.rta.dignify.service;

import com.rta.dignify.domain.*;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.dto.feed.FeedResponse;
import com.rta.dignify.dto.pick.*;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Service
public class PickService {

    static final int PAGE_SIZE = 20;

    private final PickReactionRepository pickReactionRepository;
    private final PickTrackRepository pickTrackRepository;
    private final PickRepository pickRepository;
    private final UserHypeTrackRepository userHypeTrackRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;

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

        if (containsProfanity(trimmed)) {
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

    private static boolean containsProfanity(String title) {
        String normalized = NOT_LETTER_OR_DIGIT.matcher(title.toLowerCase(Locale.ROOT)).replaceAll("");
        return BLOCKED_WORDS.stream().anyMatch(normalized::contains);
    }

    /// 공백·특수문자를 걷어내 `ㅅ ㅂ` / `f.u.c.k` 수준의 끼워넣기만 무력화한다.
    /// **자모 분해도 leet 치환도 하지 않는다** — 정교해질수록 오탐이 늘고, 여기선 오탐이 미탐보다 나쁘다.
    /// 유저는 왜 막혔는지 모른 채 이탈하고 우리는 그 사건을 400 카운트로밖에 못 본다.
    /// 호환 자모(ㄱ-ㅎㅏ-ㅣ)를 남기는 게 핵심이다 — 지우면 `ㅅㅂ`가 빈 문자열이 돼 통과한다.
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^0-9a-z가-힣ㄱ-ㅎㅏ-ㅣ]");

    /// 이 필터는 어뷰징 방어선이 아니라 **게시 전 필터가 존재한다**는 심사 요건을 만족시키는 관문이다.
    private static final Set<String> BLOCKED_WORDS = Set.of(
            // 욕설 (ko) — 변형 표기 포함
            "씨발", "시발", "씨팔", "시팔", "씨벌", "시벌", "쓰벌", "ㅅㅂ", "ㅆㅂ",
            "개새끼", "씹새끼", "십새끼", "씹창", "호로새끼", "호로자식", "개자식", "개놈",
            "병신", "븅신", "빙신", "ㅂㅅ", "ㅄ",
            "지랄", "ㅈㄹ", "좆", "좇", "미친놈", "미친년", "썅", "니미럴", "애미없", "엠창",
            "개소리", "엿먹",
            // 성적·폭력 (ko)
            "섹스", "야동", "딸딸이", "강간", "성폭행", "창녀", "매춘", "성매매",
            // en — "class"의 ass처럼 흔한 단어에 박히는 것들(ass · cock · tit · anal · rape)은 뺐다
            "fuck", "fuk", "shit", "bitch", "cunt", "whore", "slut", "pussy",
            "nigger", "nigga", "faggot", "asshole", "motherfuck",
            "porn", "dildo", "blowjob", "jizz", "rapist", "pedophile", "retard"
    );
}
