package com.rta.dignify.service;

import com.rta.dignify.domain.Pick;
import com.rta.dignify.domain.PickReaction;
import com.rta.dignify.domain.PickTrack;
import com.rta.dignify.dto.pick.PickCursor;
import com.rta.dignify.dto.pick.PickListResponse;
import com.rta.dignify.dto.pick.PickReactionCount;
import com.rta.dignify.dto.pick.PickResponse;
import com.rta.dignify.repository.PickReactionRepository;
import com.rta.dignify.repository.PickRepository;
import com.rta.dignify.repository.PickTrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class PickService {

    static final int PAGE_SIZE = 20;

    private final PickReactionRepository pickReactionRepository;
    private final PickTrackRepository pickTrackRepository;
    private final PickRepository pickRepository;

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
}
