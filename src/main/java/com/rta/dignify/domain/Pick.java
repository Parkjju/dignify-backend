package com.rta.dignify.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
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

    /// 픽 상세(= 재생 진입)가 몇 번 열렸나. 창작자에게 보이는 신호가 🔥뿐이라 실제 도달의
    /// 6.9%만 보이던 걸 메운다(`TODO.md` P1).
    ///
    /// `@ColumnDefault`가 없으면 **배포가 터진다** — `picks`에는 DEFAULT가 하나도 없어서
    /// `ddl-auto=update`가 내는 `add column … not null`이 기존 행에서 NOT NULL 위반이 된다.
    /// `User.diggingMode`와 같은 이유.
    @ColumnDefault("0")
    @Column(name = "play_count", nullable = false)
    private Integer playCount = 0;

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

    /// 상세를 한 번 열었다. 목록 조회에서는 부르지 않는다 — 스크롤이 곧 재생이 돼버린다.
    ///
    /// ponytail: 읽고-더하고-쓰기라 같은 픽을 같은 순간에 연 두 요청이 겹치면 한 건을 잃는다.
    /// 하루 수십 건 규모에서 잃는 게 한 건이라 원자 UPDATE는 아직 안 산다. 규모가 오르면 그때 바꾼다.
    public void play() {
        this.playCount++;
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    /// 반응 마일스톤 푸시를 보낸 지점을 기록한다(§10.5).
    /// 이 값이 없으면 누가 🔥를 눌렀다 껐다 다시 눌러 4→5→4→5가 될 때 같은 알림이 두 번 간다.
    public void markNotified(int reactionCount) {
        this.maxNotifiedReactions = reactionCount;
    }
}
