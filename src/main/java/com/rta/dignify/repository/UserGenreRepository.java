package com.rta.dignify.repository;

import com.rta.dignify.domain.UserGenre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserGenreRepository extends JpaRepository<UserGenre, Long> {
    List<UserGenre> findUserGenresByUserId(Long userId);
}
