package com.rta.dignify.repository;

import com.rta.dignify.domain.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthRepository extends JpaRepository<UserAuth, Long> {
    Optional<UserAuth> findByProviderAndProviderUserId(String provider, String providerUserId);
}
