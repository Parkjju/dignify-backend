package com.rta.dignify.repository;

import com.rta.dignify.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByNickname(String nickname);

    /** 구글 로그인이 기존 계정을 찾아 연결할 때 쓴다. email은 unique라 결과는 최대 1건. */
    Optional<User> findByEmail(String email);
}
