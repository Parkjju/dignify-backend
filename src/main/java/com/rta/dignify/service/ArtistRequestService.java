package com.rta.dignify.service;

import com.rta.dignify.domain.ArtistRequest;
import com.rta.dignify.domain.RequestStatus;
import com.rta.dignify.domain.User;
import com.rta.dignify.dto.artistrequest.ArtistRequestResponse;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
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

    @Transactional
    public void delete(Long userId, Long id) {
        ArtistRequest req = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_REQUEST_NOT_FOUND));
        if (!req.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ARTIST_REQUEST_NOT_FOUND);   // 남의 것 = 없는 것처럼(존재 노출 방지)
        }
        repository.delete(req);
    }

    @Transactional
    public void resolve(Long id, RequestStatus status, String cancelReason) {
        ArtistRequest req = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_REQUEST_NOT_FOUND));
        // ADDED여도 푸시를 안 보낸다 — 한 유저가 요청을 수십 건 넣으면 알림이 그만큼 울린다.
        // 발송은 어드민 푸시 탭에서 userId로 직접, 요청 여러 건을 한 문장으로 묶어서.
        req.resolve(status, cancelReason);
    }
}
