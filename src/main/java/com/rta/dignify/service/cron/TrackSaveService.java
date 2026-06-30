package com.rta.dignify.service.cron;

import com.rta.dignify.domain.Track;
import com.rta.dignify.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TrackSaveService {
    private final TrackRepository trackRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTrack(Track track) {
        trackRepository.save(track);
    }
}
