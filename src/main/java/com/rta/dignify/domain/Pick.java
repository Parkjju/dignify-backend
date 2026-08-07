package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

// 목록 인덱스는 부분 인덱스(WHERE is_deleted = FALSE)라 @Index로 표현이 안 된다 → DDL에서 직접 생성 (§10.1)
@Table(name = "picks")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pick extends BaseTimeEntity {

    @Id
    @Column(name = "pick_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "is_official", nullable = false)
    private Boolean isOfficial = false;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "max_notified_reactions", nullable = false)
    private Integer maxNotifiedReactions = 0;

    private Pick(User user, String title, Boolean isOfficial) {
        this.user = user;
        this.title = title;
        this.isOfficial = isOfficial;
    }

    public static Pick create(User user, String title, Boolean isOfficial) {
        return new Pick(user, title, isOfficial);
    }

    public void delete() {
        this.isDeleted = true;
    }

    public void changeTitle(String title) {
        this.title = title;
    }
}
