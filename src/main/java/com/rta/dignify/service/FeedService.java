package com.rta.dignify.service;

import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.feed.FeedCursor;
import com.rta.dignify.dto.feed.FeedItem;
import com.rta.dignify.dto.feed.FeedResponse;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserHypeTrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
@Service
public class FeedService {
    static final Integer FETCH_LIMIT = 10;

    private final TrackRepository trackRepository;
    private final UserHypeTrackRepository userHypeTrackRepository;

    @Transactional
    public FeedResponse getFeedList(Long userId, String cursorString) {
        List<Track> result;
        FeedResponse response;
        FeedCursor currentCursor;
        FeedCursor newCursor;
        if (cursorString == null) {
            currentCursor = new FeedCursor(FeedCursor.Phase.GENRE, 0, 0, ThreadLocalRandom.current().nextInt());
        } else {
            currentCursor = FeedCursor.decode(cursorString);
        }

        if (currentCursor.phase() == FeedCursor.Phase.GENRE) {
            result = trackRepository.findByGenreIdsExceptHypedTrackWithLimitAndOffset(userId, FeedService.FETCH_LIMIT, currentCursor.genreOffset(), currentCursor.seed());
        } else {
            result = new ArrayList<>();
        }

        if (result.size() == FeedService.FETCH_LIMIT) {
            newCursor = new FeedCursor(currentCursor.phase(), currentCursor.genreOffset() + FeedService.FETCH_LIMIT, currentCursor.generalOffset(), currentCursor.seed());
            List<FeedItem> feedItems = result.stream().map((track) -> FeedItem.from(track, false)).toList();
            response = new FeedResponse(feedItems, newCursor.encode(), true, false);
        } else {
            // 장르 조회에서 부족한 결과를 general 조회로 채우기 → 이 페이지는 장르 풀 소진.
            List<Track> paddingResponse = trackRepository.findGeneralTracksByGenreIdsExceptHypedTrackWithLimitAndOffset(userId, FETCH_LIMIT - result.size(), currentCursor.generalOffset(), currentCursor.seed());
            result.addAll(paddingResponse);
            List<FeedItem> feedItems = result.stream().map((track) -> FeedItem.from(track, false)).toList();
            newCursor = new FeedCursor(FeedCursor.Phase.GENERAL, currentCursor.genreOffset() + (FETCH_LIMIT - paddingResponse.size()), currentCursor.generalOffset() + paddingResponse.size(), currentCursor.seed());
            if (result.size() < FeedService.FETCH_LIMIT)  {
                response = new FeedResponse(feedItems, null, false, true);
            } else {
                response = new FeedResponse(feedItems, newCursor.encode(), true, true);
            }
        }
        return response;
    }

    @Transactional(readOnly = true)
    public FeedResponse searchFeedList(Long userId, String cursorString, String searchKeyword) {
        List<Track> result;
        FeedResponse response;
        FeedCursor currentCursor;
        FeedCursor newCursor;
        if (cursorString == null) {
            currentCursor = new FeedCursor(FeedCursor.Phase.GENRE, 0, 0, ThreadLocalRandom.current().nextInt());
        } else {
            currentCursor = FeedCursor.decode(cursorString);
        }

        result = trackRepository.findTracksWithSearchKeyword(searchKeyword, FeedService.FETCH_LIMIT, currentCursor.genreOffset());
        List<FeedItem> feedItems = result.stream().map((track) -> {
            boolean isHyped = userHypeTrackRepository.existsByUser_IdAndTrack_Id(userId, track.getId());
            return FeedItem.from(track, isHyped);
        }).toList();

        if (result.size() == FeedService.FETCH_LIMIT) {
            newCursor = new FeedCursor(currentCursor.phase(), currentCursor.genreOffset() + FeedService.FETCH_LIMIT, 0, currentCursor.seed());
            response = new FeedResponse(feedItems, newCursor.encode(), true, false);
        } else {
            response = new FeedResponse(feedItems, null, false, false);
        }

        return response;
    }
}
