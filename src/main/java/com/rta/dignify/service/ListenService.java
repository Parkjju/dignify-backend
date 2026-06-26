package com.rta.dignify.service;

import com.rta.dignify.domain.ListenedTrack;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.ListenedTrackRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ListenService {

    private final ListenedTrackRepository listenedTrackRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;

    @Transactional
    public void recordListenedTrack(Long userId, Long trackId) {
        User user = userRepository.getReferenceById(userId);
        Track track = trackRepository.findById(trackId).orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
        ListenedTrack listenedTrack = ListenedTrack.create(user, track);
        listenedTrackRepository.save(listenedTrack);
    }
}
