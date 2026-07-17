package com.rta.dignify.service;

import com.rta.dignify.domain.RequestStatus;
import com.rta.dignify.domain.User;
import com.rta.dignify.dto.artistrequest.ArtistRequestResponse;
import com.rta.dignify.repository.ArtistRequestRepository;
import com.rta.dignify.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class ArtistRequestServiceTest {
    @Autowired
    ArtistRequestService artistRequestService;

    @Autowired
    ArtistRequestRepository artistRequestRepository;

    @Autowired
    UserRepository userRepository;

    User user;

    @Test
    @DisplayName("아티스트 요청 생성 — PENDING으로 저장되고 응답에 값이 담긴다")
    void createArtistRequest() {
        ArtistRequestResponse res = artistRequestService.create(user.getId(), "Radiohead");

        assertThat(res.id()).isNotNull();
        assertThat(res.artistName()).isEqualTo("Radiohead");
        assertThat(res.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(res.cancelReason()).isNull();
        assertThat(res.createdAt()).isNotNull();   // IDENTITY라 save 시 즉시 INSERT·flush → 채워짐
        assertThat(artistRequestRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("아티스트 요청 생성 — 앞뒤 공백은 trim된다")
    void createTrimsWhitespace() {
        ArtistRequestResponse res = artistRequestService.create(user.getId(), "  aespa  ");

        assertThat(res.artistName()).isEqualTo("aespa");
    }

    @Test
    @DisplayName("요청 히스토리 — 최신순(id 내림차순)으로 반환한다")
    void historyNewestFirst() {
        artistRequestService.create(user.getId(), "first");
        artistRequestService.create(user.getId(), "second");
        artistRequestService.create(user.getId(), "third");

        List<ArtistRequestResponse> history = artistRequestService.history(user.getId());

        assertThat(history).extracting(ArtistRequestResponse::artistName)
                .containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("요청 히스토리 — 다른 유저의 요청은 섞이지 않는다")
    void historyIsUserScoped() {
        User other = userRepository.save(User.create("other@gmail.com", "other"));
        artistRequestService.create(user.getId(), "mine");
        artistRequestService.create(other.getId(), "theirs");

        List<ArtistRequestResponse> history = artistRequestService.history(user.getId());

        assertThat(history).extracting(ArtistRequestResponse::artistName)
                .containsExactly("mine");
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create("test@gmail.com", "nickname"));
    }
}
