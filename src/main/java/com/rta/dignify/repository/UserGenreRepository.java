package com.rta.dignify.repository;

import com.rta.dignify.domain.UserGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserGenreRepository extends JpaRepository<UserGenre, Long> {
    @Query("SELECT ug FROM UserGenre ug JOIN FETCH ug.genre WHERE ug.user.id = :userId")
    List<UserGenre> findUserGenresByUserId(@Param("userId") Long userId);

    void deleteByUser_Id(Long userId);
}
