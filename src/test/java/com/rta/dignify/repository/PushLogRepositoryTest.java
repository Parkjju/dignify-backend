package com.rta.dignify.repository;

import com.rta.dignify.domain.PushLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PushLogRepositoryTest {

    @Autowired
    PushLogRepository pushLogRepository;

    @Test
    @DisplayName("발송 이력은 최근 keep건만 남고, 실패 사유 JSON이 그대로 돌아온다")
    void trimKeepsLatest() {
        for (int i = 0; i < 5; i++) {
            PushLog log = PushLog.start("broadcast", "notice", "t" + i, "b", null, null, false);
            log.attempted();
            log.failed(1L, 2L, false, "BadDeviceToken", true);
            pushLogRepository.saveAndFlush(log);
        }

        pushLogRepository.trim(3);

        List<PushLog> left = pushLogRepository.findTop100ByOrderByIdDesc();
        assertThat(left).extracting(PushLog::getTitle).containsExactly("t4", "t3", "t2");
        assertThat(left.get(0).getFailureReasons()).containsEntry("ios:BadDeviceToken", 1);
        assertThat(left.get(0).getFailureSample()).hasSize(1);
    }
}
