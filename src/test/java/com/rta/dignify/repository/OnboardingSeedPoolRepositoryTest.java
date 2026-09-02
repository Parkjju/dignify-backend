package com.rta.dignify.repository;

import com.rta.dignify.domain.Genre;
import com.rta.dignify.domain.OnboardingSeedPool;
import com.rta.dignify.domain.Track;
import com.rta.dignify.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class OnboardingSeedPoolRepositoryTest {

    @Autowired
    OnboardingSeedPoolRepository onboardingSeedPoolRepository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    @DisplayName("심어 둔 순서로 나오고 비활성 곡은 빠진다")
    void 순서와_비활성_제외() {
        Genre genre = Genre.create("Rock", "락");
        entityManager.persistAndFlush(genre);
        Track second = track("second", genre);
        Track first = track("first", genre);
        Track dead = track("dead", genre);
        // 넣는 순서와 position을 일부러 어긋나게 둔다 — id 순으로 나오면 통과하지 못한다.
        entityManager.persistAndFlush(OnboardingSeedPool.create(second, 2));
        entityManager.persistAndFlush(OnboardingSeedPool.create(first, 1));
        entityManager.persistAndFlush(OnboardingSeedPool.create(dead, 3));
        // 곡을 끄는 경로는 크론(수집 배치)이라 엔티티에 setter가 없다. 상태만 만들면 되므로 직접 끈다.
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE tracks SET is_active = false WHERE track_id = :id")
                .setParameter("id", dead.getId()).executeUpdate();
        entityManager.clear();

        List<OnboardingSeedPool> found = onboardingSeedPoolRepository.findAllActiveOrdered();

        assertThat(found).extracting(s -> s.getTrack().getTrackName())
                .containsExactly("first Track", "second Track");
        // 장르까지 같이 당겨 온다(FeedItem이 genreName을 읽는다). 안 걸려 있으면 LazyInitialization이 난다.
        assertThat(found.get(0).getTrack().getGenre().getGenreNameEn()).isEqualTo("Rock");
    }

    private Track track(String prefix, Genre genre) {
        Track track = Track.create(prefix, prefix + " Artist", prefix + " Album", prefix + " Track",
                "https://example.com/preview/" + prefix + ".mp3", "https://example.com/track/" + prefix,
                "https://example.com/art/" + prefix + ".jpg", Instant.now(), genre, "US", "ITUNES");
        entityManager.persistAndFlush(track);
        return track;
    }
}
