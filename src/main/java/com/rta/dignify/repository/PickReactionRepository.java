package com.rta.dignify.repository;

import com.rta.dignify.domain.PickReaction;
import com.rta.dignify.dto.pick.PickReactionCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    Optional<PickReaction> findByPickIdAndUserId(Long pickId, Long userId);

    /// 마일스톤 푸시(§10.5) 판정용. **소유자 본인 반응은 뺀다** —
    /// 안 빼면 자기 픽에 🔥 누르고 자기가 "첫 반응" 알림을 받는다.
    @Query(value = """
        SELECT COUNT(pr) FROM PickReaction pr
        WHERE pr.pick.id = :pickId AND pr.user.id <> :ownerId
    """)
    long countByPickIdExcludingOwner(@Param("pickId") Long pickId, @Param("ownerId") Long ownerId);
}
