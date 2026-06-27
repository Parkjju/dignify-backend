package com.rta.dignify.service;

import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserGenre;
import com.rta.dignify.dto.genre.GenreResponse;
import com.rta.dignify.dto.user.NicknameUpdateRequest;
import com.rta.dignify.dto.user.NicknameUpdateResponse;
import com.rta.dignify.dto.user.UserProfileResponse;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.UserGenreRepository;
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

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.getReferenceById(userId);
        List<GenreResponse> genreList = userGenreRepository.findUserGenresByUserId(userId).stream().map((genre) -> GenreResponse.from(genre.getGenre())).toList();

        return new UserProfileResponse(user.getNickname(), user.getIsOnboardingComplete(), genreList);
    }

    @Transactional
    public NicknameUpdateResponse changeUserNickname(Long userId, NicknameUpdateRequest request) {
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
}
