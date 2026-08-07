package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Table(name = "pick_reactions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pick_user", columnNames = {"pick_id", "user_id"})
})
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PickReaction extends BaseTimeEntity {

    @Id
    @Column(name = "pick_reaction_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pick_id", updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Pick pick;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "emoji", length = 16, nullable = false)
    private String emoji;

    private PickReaction(Pick pick, User user, String emoji) {
        this.pick = pick;
        this.user = user;
        this.emoji = emoji;
    }

    public static PickReaction create(Pick pick, User user,String emoji) {
        return new PickReaction(pick, user, emoji);
    }

    public void changeEmoji(String emoji) {
        this.emoji = emoji;
    }
}
