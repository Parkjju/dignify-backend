package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserGenre;
import com.rta.dignify.dto.genre.GenreResponse;
import com.rta.dignify.dto.user.NicknameUpdateRequest;
import com.rta.dignify.dto.user.NicknameUpdateResponse;
import com.rta.dignify.dto.user.PreferGenreUpdateRequest;
import com.rta.dignify.dto.user.UserProfileResponse;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.global.util.ProfanityFilter;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.dto.user.DiggingModeUpdateRequest;
import com.rta.dignify.dto.user.SeedTracksUpdateRequest;
import com.rta.dignify.dto.user.AdPushConsentResponse;
import com.rta.dignify.repository.UserDeviceTokenRepository;
import com.rta.dignify.repository.UserGenreRepository;
import com.rta.dignify.repository.UserHypeTrackRepository;
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserGenreRepository userGenreRepository;
    private final GenreRepository genreRepository;
    private final UserHypeTrackRepository userHypeTrackRepository;
    private final UserDeviceTokenRepository userDeviceTokenRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.getReferenceById(userId);
        List<GenreResponse> genreList = userGenreRepository.findUserGenresByUserId(userId).stream().map((genre) -> GenreResponse.from(genre.getGenre())).toList();

        return new UserProfileResponse(user.getNickname(), user.getIsOnboardingComplete(), genreList,
                Boolean.TRUE.equals(user.getDiggingMode()), Boolean.TRUE.equals(user.getAdPushConsent()));
    }

    @Transactional
    public NicknameUpdateResponse changeUserNickname(Long userId, NicknameUpdateRequest request) {
        // @Pattern이 걸러주는 건 문자셋뿐이라 금칙어는 여기서 따로 본다 (ProfanityFilter, PickService.validatedTitle과 공용).
        if (ProfanityFilter.contains(request.nickname())) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_INVALID);
        }
        User user = userRepository.getReferenceById(userId);
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.USER_NICKNAME_DUPLICATE);
        }
        user.changeNickname(request.nickname());
        return new NicknameUpdateResponse(request.nickname());
    }

    @Transactional
    public void completeOnboarding(Long userId) {
        User user = userRepository.getReferenceById(userId);
        user.completeOnboarding();
    }

    @Transactional
    public void changeDiggingMode(Long userId, DiggingModeUpdateRequest request) {
        userRepository.getReferenceById(userId).changeDiggingMode(request.enabled());
    }

    /// 광고성 알림 수신 동의·거부. 처리 시각을 돌려준다 — 앱의 결과 통지가 이 값을 쓴다.
    @Transactional
    public AdPushConsentResponse changeAdPushConsent(Long userId, boolean consent) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changeAdPushConsent(consent, clock.instant());
        return new AdPushConsentResponse(consent, user.getAdPushConsentAt());
    }

    /// 알림의 「수신거부」 버튼. 로그인 없이 기기 토큰만으로 그 기기 주인의 동의를 거둔다 —
    /// 수신거부에 로그인을 요구하면 위반이다(KISA 안내서 44쪽).
    @Transactional
    public AdPushConsentResponse optOutByDeviceToken(String deviceToken) {
        User user = userDeviceTokenRepository.findByToken(deviceToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_TOKEN_NOT_FOUND)).getUser();
        user.changeAdPushConsent(false, clock.instant());
        return new AdPushConsentResponse(false, user.getAdPushConsentAt());
    }

    /// 추천 기준 곡을 통째로 갈아 끼운다. 지우고 다시 세우는 편이 차집합을 계산하는 것보다
    /// 짧고, 두 UPDATE가 한 트랜잭션 안이라 중간 상태가 밖에서 보이지 않는다.
    @Transactional
    public void changeSeedTracks(Long userId, SeedTracksUpdateRequest request) {
        userHypeTrackRepository.clearSeeds(userId);
        if (!request.trackIds().isEmpty()) {
            userHypeTrackRepository.markSeeds(userId, request.trackIds());
        }
    }

    @Transactional
    public void changeUserGenres(Long userId, PreferGenreUpdateRequest request) {
        userGenreRepository.deleteByUser_Id(userId);
        userGenreRepository.flush();   // DELETE를 INSERT보다 먼저 DB에 반영(uq_user_genre_id 충돌 방지)
        User user = userRepository.getReferenceById(userId);
        request.genreIds().forEach(genreId -> {
            Genre dbGenre = genreRepository.findById(genreId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GENRE_NOT_FOUND));
            UserGenre updateGenre = UserGenre.create(user, dbGenre);
            userGenreRepository.save(updateGenre);
        });
    }
}
