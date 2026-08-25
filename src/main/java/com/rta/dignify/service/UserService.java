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
import com.rta.dignify.repository.UserGenreRepository;
import com.rta.dignify.repository.UserHypeTrackRepository;
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserGenreRepository userGenreRepository;
    private final GenreRepository genreRepository;
    private final UserHypeTrackRepository userHypeTrackRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.getReferenceById(userId);
        List<GenreResponse> genreList = userGenreRepository.findUserGenresByUserId(userId).stream().map((genre) -> GenreResponse.from(genre.getGenre())).toList();

        return new UserProfileResponse(user.getNickname(), user.getIsOnboardingComplete(), genreList,
                Boolean.TRUE.equals(user.getDiggingMode()));
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
