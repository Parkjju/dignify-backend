package com.rta.dignify.service;

import com.rta.dignify.domain.ArtistRequest;
import com.rta.dignify.domain.User;
import com.rta.dignify.dto.artistrequest.ArtistRequestResponse;
import com.rta.dignify.repository.ArtistRequestRepository;
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ArtistRequestService {
    private final ArtistRequestRepository repository;
    private final UserRepository userRepository;

    @Transactional
    public ArtistRequestResponse create(Long userId, String artistName) {
        User user = userRepository.getReferenceById(userId);   // ListenService와 동일 패턴
        return ArtistRequestResponse.from(repository.save(ArtistRequest.create(user, artistName.trim())));
    }

    @Transactional(readOnly = true)
    public List<ArtistRequestResponse> history(Long userId) {
        return repository.findByUserIdOrderByIdDesc(userId).stream()
                .map(ArtistRequestResponse::from).toList();
    }
}
