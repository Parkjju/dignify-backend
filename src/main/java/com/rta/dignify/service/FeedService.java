package com.rta.dignify.service;

import com.rta.dignify.domain.Track;
import com.rta.dignify.dto.feed.FeedCursor;
import com.rta.dignify.dto.feed.FeedResponse;
import com.rta.dignify.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
@Service
public class FeedService {
    private static final Integer FETCH_LIMIT = 10;
    private final TrackRepository trackRepository;

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
            result = trackRepository.findByGenreIdsExceptHypedTrackWithLimitAndOffset(userId, FeedService.FETCH_LIMIT, currentCursor.genreOffset());
        } else {
            result = new ArrayList<>();
        }

        if (result.size() == FeedService.FETCH_LIMIT) {
            newCursor = new FeedCursor(currentCursor.phase(), currentCursor.genreOffset() + FeedService.FETCH_LIMIT, currentCursor.generalOffset(), currentCursor.seed());
            response = new FeedResponse(result, newCursor.encode(), true);
        } else {
            // 장르 조회에서 부족한 결과를 general 조회로 채우기
            List<Track> paddingResponse = trackRepository.findGeneralTracksByGenreIdsExceptHypedTrackWithLimitAndOffset(userId, FETCH_LIMIT - result.size(), currentCursor.generalOffset());
            result.addAll(paddingResponse);
            newCursor = new FeedCursor(FeedCursor.Phase.GENERAL, currentCursor.genreOffset() + (FETCH_LIMIT - paddingResponse.size()), currentCursor.generalOffset() + paddingResponse.size(), currentCursor.seed());
            if (result.size() < FeedService.FETCH_LIMIT)  {
                response = new FeedResponse(result, null, false);
            } else {
                response = new FeedResponse(result, newCursor.encode(), true);
            }
        }
        return response;
    }
}
