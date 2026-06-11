package com.rta.dignify.repository;

import com.rta.dignify.domain.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
}
