package com.rta.dignify.repository;

import com.rta.dignify.domain.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {
    Optional<UserDeviceToken> findByToken(String token);

    List<UserDeviceToken> findByUserId(Long userId);

    /// 어드민 푸시 대상 목록용. 유저별로 묶어 보여주므로 유저를 함께 가져온다.
    @Query("SELECT t FROM UserDeviceToken t JOIN FETCH t.user u ORDER BY u.id")
    List<UserDeviceToken> findAllWithUser();

    /// 광고성 발송 대상. 동의는 기기가 아니라 유저 단위다.
    @Query("SELECT t FROM UserDeviceToken t JOIN FETCH t.user u WHERE u.adPushConsent = TRUE AND u.deletedAt IS NULL")
    List<UserDeviceToken> findAdConsented();

    @Query("SELECT t FROM UserDeviceToken t JOIN FETCH t.user u WHERE u.adPushConsent = TRUE AND u.deletedAt IS NULL AND u.id IN :userIds")
    List<UserDeviceToken> findAdConsentedByUserIds(Collection<Long> userIds);
}
