package com.rta.dignify.service;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.Pick;
import com.rta.dignify.domain.Report;
import com.rta.dignify.domain.ReportReason;
import com.rta.dignify.domain.Track;
import com.rta.dignify.domain.User;
import com.rta.dignify.dto.pick.PickCreate;
import com.rta.dignify.dto.report.ReportCreate;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.GenreRepository;
import com.rta.dignify.repository.PickRepository;
import com.rta.dignify.repository.ReportRepository;
import com.rta.dignify.repository.TrackRepository;
import com.rta.dignify.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// `PickServiceTest`와 같은 이유로 예외 케이스는 각 테스트 마지막에 둔다.
@SpringBootTest
@Transactional
public class ReportServiceTest {

    @Autowired GenreRepository genreRepository;
    @Autowired UserRepository userRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired PickRepository pickRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired PickService pickService;
    @Autowired ReportService reportService;

    User owner;
    User reporter;
    Track track;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.create("owner@dignify.app", "owner"));
        reporter = userRepository.save(User.create("reporter@dignify.app", "reporter"));
        Genre genre = genreRepository.save(Genre.create("Rock", "락"));
        track = trackRepository.save(Track.create(
                "ext-0", "Artist", "Album", "Track",
                "https://example.com/preview.m4a", "https://example.com/track",
                "https://example.com/art.jpg", Instant.now(), genre, "KR", "ITUNES"));
    }

    @Test
    @DisplayName("detail은 OTHER일 때만 남는다 - 다른 사유로 온 자유 텍스트는 버린다")
    void detailIsKeptOnlyForOther() {
        Long contentPickId = createPick();
        Long otherPickId = createPick();

        reportService.create(reporter.getId(), new ReportCreate(contentPickId, ReportReason.CONTENT, "버려질 본문"));
        reportService.create(reporter.getId(), new ReportCreate(otherPickId, ReportReason.OTHER, "직접 적은 사유"));

        assertThat(reportOf(contentPickId).getReason()).isEqualTo(ReportReason.CONTENT);
        assertThat(reportOf(contentPickId).getDetail()).isNull();
        assertThat(reportOf(otherPickId).getDetail()).isEqualTo("직접 적은 사유");
    }

    @Test
    @DisplayName("같은 픽 중복 신고는 에러가 아니라 조용한 성공 - 행은 하나만 남는다")
    void duplicateReportSucceedsSilently() {
        Long pickId = createPick();

        reportService.create(reporter.getId(), new ReportCreate(pickId, ReportReason.CONTENT, null));
        assertThatCode(() -> reportService.create(reporter.getId(), new ReportCreate(pickId, ReportReason.NICKNAME, null)))
                .doesNotThrowAnyException();

        assertThat(reportRepository.findAll()).hasSize(1);
        // 두 번째 요청이 덮어쓰지도 않는다 - 서버는 쌓기만 한다
        assertThat(reportOf(pickId).getReason()).isEqualTo(ReportReason.CONTENT);
    }

    @Test
    @DisplayName("다른 사람이 같은 픽을 신고하는 건 별개 행이다")
    void differentReportersStackUp() {
        Long pickId = createPick();

        reportService.create(reporter.getId(), new ReportCreate(pickId, ReportReason.CONTENT, null));
        reportService.create(owner.getId(), new ReportCreate(pickId, ReportReason.NICKNAME, null));

        assertThat(reportRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("없는 픽 · 삭제된 픽 신고는 404 - FK 위반으로 500이 나면 안 된다")
    void reportRejectsMissingAndDeletedPick() {
        Long deletedPickId = createPick();
        pickService.deletePick(owner.getId(), deletedPickId);

        assertNotFound(() -> reportService.create(reporter.getId(),
                new ReportCreate(999_999L, ReportReason.CONTENT, null)));
        assertNotFound(() -> reportService.create(reporter.getId(),
                new ReportCreate(deletedPickId, ReportReason.CONTENT, null)));
    }

    // MARK: helpers

    private Long createPick() {
        pickService.createPick(owner.getId(), new PickCreate(null, List.of(track.getId())));
        return pickRepository.findAll().stream().max(Comparator.comparing(Pick::getId)).orElseThrow().getId();
    }

    private Report reportOf(Long pickId) {
        return reportRepository.findAll().stream()
                .filter(r -> r.getPick().getId().equals(pickId))
                .findFirst().orElseThrow();
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PICK_DOES_NOT_EXIST);
    }
}
