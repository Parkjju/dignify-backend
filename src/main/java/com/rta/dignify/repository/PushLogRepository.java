package com.rta.dignify.repository;

import com.rta.dignify.domain.PushLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PushLogRepository extends JpaRepository<PushLog, Long> {
    List<PushLog> findTop100ByOrderByIdDesc();

    /// 앱이 알린 열람 1건. 없는 push_id(정리됐거나 엉뚱한 값)면 0행이 바뀌고 끝난다.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE PushLog p SET p.opened = p.opened + 1 WHERE p.pushId = :pushId")
    int markOpened(String pushId);

    /// 최근 keep건만 남긴다. 발송할 때마다 불러 테이블 크기를 고정한다.
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM push_logs WHERE push_log_id <= (SELECT MAX(push_log_id) FROM push_logs) - :keep", nativeQuery = true)
    void trim(int keep);
}
