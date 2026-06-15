package com.rta.dignify.repository;

import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    Optional<UserToken> findUserTokenByRefreshTokenHash(String refreshToken);
    void deleteUserTokenByRefreshTokenHash(String refreshToken);
    void deleteAllByUser(User user);
}
