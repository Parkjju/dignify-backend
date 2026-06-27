package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserGenre;
import com.rta.dignify.dto.user.NicknameUpdateRequest;
import com.rta.dignify.dto.user.UserProfileResponse;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.UserGenreRepository;
import com.rta.dignify.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class UserServiceTest {
    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GenreRepository genreRepository;

    @Autowired
    UserGenreRepository userGenreRepository;

    @Test
    @DisplayName("유저 프로필 획득 테스트")
    void getUserProfileTest() {
        User user = User.create("test@gmail.com", "nickname");
        Genre genre = Genre.create("rock", "락");
        userRepository.save(user);
        genreRepository.save(genre);

        UserGenre userGenre = UserGenre.create(user, genre);
        userGenreRepository.save(userGenre);

        UserProfileResponse userProfile = userService.getUserProfile(user.getId());
        assertThat(userProfile.genres().getFirst().genreId()).isEqualTo(genre.getId());
        assertThat(userProfile.nickname()).isEqualTo("nickname");
    }

    @Test
    @DisplayName("유저 닉네임 변경 테스트")
    void changeUserNicknameTest() {
        User user = User.create("test@gmail.com", "nickname");
        User anotherUser = User.create("test2@gmail.com", "anotherUser");

        userRepository.save(user);
        userRepository.save(anotherUser);

        userService.changeUserNickname(user.getId(), new NicknameUpdateRequest("newNickname"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo("newNickname");

        // 닉네임 중복 테스트
        assertThatThrownBy(() -> userService.changeUserNickname(user.getId(), new NicknameUpdateRequest("anotherUser")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NICKNAME_DUPLICATE);
    }

    @Test
    @DisplayName("온보딩 완료 처리 테스트")
    void completeOnboardingTest() {
        User user = User.create("test@gmail.com", "nickname");
        userRepository.save(user);

        userService.completeOnboarding(user.getId());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getIsOnboardingComplete()).isTrue();
    }
}
