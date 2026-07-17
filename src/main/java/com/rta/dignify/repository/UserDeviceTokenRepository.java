package com.rta.dignify.repository;

import com.rta.dignify.domain.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {
    Optional<UserDeviceToken> findByToken(String token);

    List<UserDeviceToken> findByUserId(Long userId);
}
