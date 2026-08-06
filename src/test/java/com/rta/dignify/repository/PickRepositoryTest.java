package com.rta.dignify.repository;

import com.rta.dignify.domain.Pick;
import com.rta.dignify.domain.User;
import com.rta.dignify.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@Import(JpaAuditingConfig.class)
public class PickRepositoryTest {

    @Autowired
    TestEntityManager entityManager;

    @Autowired
    PickRepository pickRepository;

    @Test
    @DisplayName("Pick 엔티티 조회 테스트")
    void picksFetchTest() {
        User curator = User.create("curator@gmail.com", "curator");
        entityManager.persistAndFlush(curator);

        User user = User.create("user@gmail.com", "user");
        entityManager.persistAndFlush(user);

        List<Pick> userPicks1 = new ArrayList<>();
        List<Pick> curatedPicks = new ArrayList<>();
        List<Pick> userPicks2 = new ArrayList<>();


        for (int i = 0; i < 10; i++) {
            Pick userPick = Pick.create(user, "유저의 픽 " + i, false);
            if (i == 1) {
                userPick.delete();
            } else {
                userPicks1.add(userPick);
            }
            entityManager.persistAndFlush(userPick);
        }

        for (int i = 0; i < 10; i++) {
            Pick curatedPick = Pick.create(curator, "큐레이터의 픽 " + i, true);
            curatedPicks.add(curatedPick);
            entityManager.persistAndFlush(curatedPick);
        }

        for (int i = 10; i < 20; i++) {
            Pick userPick = Pick.create(user, "유저의 픽 " + i, false);
            userPicks2.add(userPick);
            entityManager.persistAndFlush(userPick);
        }

        // 1. delete된 데이터는 필터링하고 조회하는지
        assertThat(pickRepository.findPage(null, null, PageRequest.of(0, 30))).hasSize(29);

        // 2. 사용자 픽이 먼저 등장하는지
        assertThat(pickRepository.findPage(null, null, PageRequest.of(0, 30))).extracting(Pick::getId).containsExactlyElementsOf(Stream.concat(Stream.concat(userPicks2.reversed().stream(), userPicks1.reversed().stream()), curatedPicks.reversed().stream()).map(Pick::getId).toList());

        // 3. 커서 경계 테스트
        Long lastUserPickId = userPicks1.getFirst().getId();   // 사용자 픽 중 가장 작은 id
        List<Pick> next = pickRepository.findPage(false, lastUserPickId, PageRequest.of(0, 10));
        assertThat(next).extracting(Pick::getId)
                .containsExactlyElementsOf(curatedPicks.reversed().stream().map(Pick::getId).toList());
    }
}
