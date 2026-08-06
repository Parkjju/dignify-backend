package com.rta.dignify.repository;

import com.rta.dignify.domain.PickReaction;
import com.rta.dignify.dto.pick.PickReactionCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PickReactionRepository extends JpaRepository<PickReaction, Long> {

    @Query(value = """
        SELECT new com.rta.dignify.dto.pick.PickReactionCount(
            pr.pick.id, pr.emoji, COUNT(pr)
        )
        FROM PickReaction pr
        WHERE pr.pick.id IN :pickIds
        GROUP BY pr.pick.id, pr.emoji
    """)
    List<PickReactionCount> countPickReactionsByPickIds(@Param("pickIds") List<Long> pickIds);

    @Query(value = """
        SELECT pr FROM PickReaction pr
        WHERE pr.pick.id IN :pickIds AND pr.user.id = :userId
    """)
    List<PickReaction> findPickReactionsByUserIdInPickIds(@Param("pickIds") List<Long> pickIds, @Param("userId") Long userId);
}
