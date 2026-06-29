package com.rta.dignify.service;

import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserHypeTrack;
import com.rta.dignify.dto.hype.HypeItem;
import com.rta.dignify.dto.hype.HypeListResponse;
import com.rta.dignify.dto.track.TrackDetailResponse;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserHypeTrackRepository;
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class HypeService {
    private final UserHypeTrackRepository userHypeTrackRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    private static final int PAGE_SIZE = 10;

    @Transactional
    public void registerHype(Long userId, Long trackId) {
        boolean isHypeRegistered = userHypeTrackRepository.existsByUser_IdAndTrack_Id(userId, trackId);
        if (isHypeRegistered) {
            throw new BusinessException(ErrorCode.HYPE_ALREADY_REGISTERED);
        } else {
            User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            Track track = trackRepository.findById(trackId).orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
            UserHypeTrack userHypeTrack = UserHypeTrack.create(user, track);
            userHypeTrackRepository.save(userHypeTrack);
        }
    }

    @Transactional
    public void deleteHype(Long userId, Long trackId) {
        UserHypeTrack userHypeTrack = userHypeTrackRepository.findByUser_IdAndTrack_Id(userId, trackId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HYPE_NOT_FOUND));
        userHypeTrackRepository.delete(userHypeTrack);
    }

    @Transactional
    public HypeListResponse getMyHypedTracks(Long userId, Long cursor) {
        List<UserHypeTrack> userHypeTrackList = userHypeTrackRepository.findUserHypeTracksByUserId(userId, cursor, PageRequest.of(0, PAGE_SIZE));
        HypeListResponse hypeListResponse;
        List<HypeItem> hypeItems = userHypeTrackList.stream().map(HypeItem::from).toList();
        if (userHypeTrackList.size() < PAGE_SIZE) {
            hypeListResponse = new HypeListResponse(hypeItems, null);
        } else {
            hypeListResponse = new HypeListResponse(hypeItems, hypeItems.getLast().userHypeTrackId());
        }
        return hypeListResponse;
    }

    @Transactional(readOnly = true)
    public TrackDetailResponse getTrackDetails(Long trackId) {
        Track track = trackRepository.findById(trackId).orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
        List<UserHypeTrack> userHypeTrack = userHypeTrackRepository.findFirstFiveHypeUsers(trackId);
        return TrackDetailResponse.from(track, userHypeTrack);
    }
}
