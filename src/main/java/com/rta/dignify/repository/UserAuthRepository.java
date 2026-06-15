package com.rta.dignify.repository;

import com.rta.dignify.domain.User;
import com.rta.dignify.domain.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserAuthRepository extends JpaRepository<UserAuth, Long> {
    @Query("SELECT ua.user FROM UserAuth ua JOIN FETCH ua.user WHERE ua.provider = :provider AND ua.providerUserId = :providerUserId")
    Optional<User> findUserByProviderAndProviderUserId(String provider, String providerUserId);
}
