package com.rta.dignify.service;

import com.rta.dignify.domain.RequestStatus;
import com.rta.dignify.domain.User;
import com.rta.dignify.dto.artistrequest.ArtistRequestResponse;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

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

    @Test
    @DisplayName("본인 요청 삭제 — 목록에서 사라진다")
    void deleteOwnRequest() {
        Long id = artistRequestService.create(user.getId(), "toDelete").id();

        artistRequestService.delete(user.getId(), id);

        assertThat(artistRequestRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("남의 요청 삭제 시도 — ARTIST_REQUEST_NOT_FOUND, 원본은 유지된다")
    void deleteOthersRequestIsRejected() {
        User other = userRepository.save(User.create("other@gmail.com", "other"));
        Long id = artistRequestService.create(other.getId(), "theirs").id();

        assertThatThrownBy(() -> artistRequestService.delete(user.getId(), id))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARTIST_REQUEST_NOT_FOUND);
        assertThat(artistRequestRepository.findById(id)).isPresent();   // 남의 것은 안 지워짐
    }

    @Test
    @DisplayName("존재하지 않는 요청 삭제 — ARTIST_REQUEST_NOT_FOUND")
    void deleteMissingRequest() {
        assertThatThrownBy(() -> artistRequestService.delete(user.getId(), 9999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARTIST_REQUEST_NOT_FOUND);
    }

    @Test
    @DisplayName("resolve — ADDED로 상태가 바뀐다")
    void resolveToAdded() {
        Long id = artistRequestService.create(user.getId(), "Radiohead").id();

        artistRequestService.resolve(id, RequestStatus.ADDED, null);

        assertThat(artistRequestRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.ADDED);
    }

    @Test
    @DisplayName("resolve — CANCELED로 바뀌고 사유가 저장된다")
    void resolveToCanceled() {
        Long id = artistRequestService.create(user.getId(), "xyz").id();

        artistRequestService.resolve(id, RequestStatus.CANCELED, "Not on our music source");

        var saved = artistRequestRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.CANCELED);
        assertThat(saved.getCancelReason()).isEqualTo("Not on our music source");
    }

    @Test
    @DisplayName("resolve — 존재하지 않는 요청이면 ARTIST_REQUEST_NOT_FOUND")
    void resolveMissingRequest() {
        assertThatThrownBy(() -> artistRequestService.resolve(9999L, RequestStatus.ADDED, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ARTIST_REQUEST_NOT_FOUND);
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create("test@gmail.com", "nickname"));
    }
}
