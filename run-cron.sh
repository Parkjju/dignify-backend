#!/bin/bash
set -e

usage() {
    cat <<'EOF'
run-cron.sh — Cloud SQL 프록시 + bootRun 띄우고 크론잡 트리거

Usage:
  ./run-cron.sh collect <endIndex>              id 브루트포스로 트랙 수집
  ./run-cron.sh collect-artist "A" "B" ...      아티스트명으로 수집 (여러 명 가능)
                                                동명이인이면 중단하고 후보 artistId를 로그에 찍는다
  ./run-cron.sh collect-artist-id 123 456       중단된 건을 artistId로 지정해 수집
  ./run-cron.sh enrich-ko                       한글 로컬라이즈 보강
  ./run-cron.sh resolve-artist                  유저가 등록 요청한 대기중 아티스트 목록 보기
  ./run-cron.sh resolve-artist 3 7              해당 요청을 ADDED로 바꾸고 요청자에게 푸시
                                                (곡 수집은 collect-artist로 먼저 직접 돌릴 것)
  ./run-cron.sh cancel-artist 9 11              해당 요청을 거절 처리 (푸시 안 나감)
  ./run-cron.sh push "제목" "본문"               전체 유저에게 공지 푸시 (확인 후 발송)
  ./run-cron.sh push-users                      기기 토큰이 등록된 유저 목록 (TO에 넣을 userId)
  ./run-cron.sh curate                          지금 나가고 있는 큐레이션 세트 보기
  ./run-cron.sh curate 12 34 56                 트랙 id를 세트에 넣기 (적은 순서대로 노출)
  ./run-cron.sh -h | --help                     이 도움말

Examples:
  ./run-cron.sh collect 50000000
  ./run-cron.sh collect-artist "Radiohead" "Aphex Twin"
  ./run-cron.sh collect-artist-id 1031084591    # "Silica Gel"처럼 동명이인이라 중단된 경우
  ./run-cron.sh resolve-artist                  # 먼저 목록으로 id 확인
  ./run-cron.sh resolve-artist 3
  REASON="Not on Apple Music" ./run-cron.sh cancel-artist 9
  ./run-cron.sh push "새 큐레이션" "이번 주 세트가 올라왔어요"
  TO=3 ./run-cron.sh push "제목" "본문"          # userId=3 기기에만 (테스트 발송)
  FORCE=true ./run-cron.sh push "제목" "본문"    # 새벽인 유저까지 전부
  MIN_BUILD=12 ./run-cron.sh push "제목" "본문"  # 빌드 12(1.0.6) 이상 기기에만
  ./run-cron.sh curate 8123 4471 9902
  REPLACE=true ./run-cron.sh curate 8123 4471    # 이번 주 세트로 통째 교체 (나머지는 끔)

거절 사유는 요청한 유저의 앱 화면에 그대로 보인다. REASON 없이 부르면 기본 문구가 들어간다.
push 문구는 보낸 그대로 알림에 뜬다(번역 없음). 기기 로컬 09~22시인 유저에게만 나가고,
그 밖은 건너뛴다 — 시간 무시하고 보내려면 FORCE=true.
TO를 주면 그 유저 기기에만, 시간대 상관없이, 확인 없이 바로 나간다. 전체 발송 전 본인 기기로
찍어보는 용도. userId는 push-users로 확인.

MIN_BUILD는 앱 빌드 번호(CFBundleVersion) 기준이다. 구버전에 없는 화면을 안내할 때 쓴다 —
받아도 눌러보면 그 화면이 없어서다. 빌드가 아직 안 잡힌 기기(앱을 한 번도 안 켠 유저)는 빠진다.
빌드별 기기 수는 push-users에 나온다.

curate는 세트에 곡 수가 몇이든 다 내보낸다. 상한이 없으니 한 번에 몇 곡만 넣을 것.
이미 들어있던 곡을 다시 넣으면 순서만 바뀌고, 껐던 곡을 넣으면 다시 켜진다.
REPLACE 없이 부르면 지난주 세트가 그대로 남아 같이 나간다.
EOF
}

case "$1" in
    -h|--help) usage; exit 0 ;;
esac

JOB="${1:-collect}"
END_INDEX="$2"

case "$JOB" in
    collect)
        if [ -z "$END_INDEX" ]; then
            echo "Usage: ./run-cron.sh collect <endIndex>"
            echo "Example: ./run-cron.sh collect 50000000"
            exit 1
        fi
        ;;
    enrich-ko) ;;
    collect-artist|collect-artist-id)
        shift
        ARTISTS=("$@")
        if [ ${#ARTISTS[@]} -eq 0 ]; then
            echo "Usage: ./run-cron.sh collect-artist \"Radiohead\" \"Aphex Twin\" ..."
            echo "       ./run-cron.sh collect-artist-id 1031084591 ..."
            exit 1
        fi
        if [ "$JOB" = "collect-artist-id" ]; then
            for a in "${ARTISTS[@]}"; do
                if ! [[ "$a" =~ ^[0-9]+$ ]]; then
                    echo "artistId는 숫자여야 합니다: '$a'"
                    exit 1
                fi
            done
        fi
        ;;
    resolve-artist|cancel-artist)
        shift
        IDS=("$@")
        if [ "$JOB" = "cancel-artist" ] && [ ${#IDS[@]} -eq 0 ]; then
            echo "Usage: ./run-cron.sh cancel-artist <id> [<id> ...]"
            echo "대기중 목록은: ./run-cron.sh resolve-artist"
            exit 1
        fi
        for id in "${IDS[@]}"; do
            if ! [[ "$id" =~ ^[0-9]+$ ]]; then
                echo "요청 id는 숫자여야 합니다: '$id'"
                exit 1
            fi
        done
        ;;
    push)
        TITLE="$2"
        BODY="$3"
        if [ -z "$TITLE" ] || [ -z "$BODY" ]; then
            echo "Usage: ./run-cron.sh push \"제목\" \"본문\""
            exit 1
        fi
        if [ -n "$TO" ] && ! [[ "$TO" =~ ^[0-9]+$ ]]; then
            echo "TO는 userId(숫자)여야 합니다: '$TO'"
            echo "userId 목록은: ./run-cron.sh push-users"
            exit 1
        fi
        if [ -n "$MIN_BUILD" ] && ! [[ "$MIN_BUILD" =~ ^[0-9]+$ ]]; then
            echo "MIN_BUILD는 빌드 번호(숫자)여야 합니다: '$MIN_BUILD'"
            echo "1.0.6은 빌드 12. 빌드별 기기 수는: ./run-cron.sh push-users"
            exit 1
        fi
        ;;
    push-users) ;;
    curate)
        shift
        TRACK_IDS=("$@")
        for id in "${TRACK_IDS[@]}"; do
            if ! [[ "$id" =~ ^[0-9]+$ ]]; then
                echo "트랙 id는 숫자여야 합니다: '$id'"
                exit 1
            fi
        done
        ;;
    *)
        echo "Unknown job: $JOB (collect | collect-artist | collect-artist-id | enrich-ko | resolve-artist | cancel-artist | push | push-users | curate)"
        exit 1
        ;;
esac

set -a && source .env && set +a

PROXY_PORT=5433
APP_PORT=8080
LOG_FILE="/tmp/dignify-bootrun.log"

# y로 시작하면 예. 정확히 "y" 한 글자만 받으면 안 된다 — 대문자 Y, "yes", 그리고 한글 자판을
# 켜둔 채 친 스페이스(non-breaking space, c2a0)가 전부 터미널에선 똑같이 "y "로 보이면서
# 조용히 취소로 떨어진다. 방금 한글로 문구를 친 직후에 누르는 자리라 실제로 걸린다.
confirm() {
    local answer
    read -r -p "$1 (y/N) " answer
    [[ "$answer" =~ ^[Yy] ]]
}

# 푸시는 APNs 키가 있는 라이브 서버에서만 나간다. 로컬 bootRun은 키가 없어 PushService 빈이 아예 안 뜨고,
# 그러면 상태만 바뀌고 알림은 조용히 안 감. 그래서 수집은 로컬, 상태 변경은 라이브로 나눠 쏜다.
LIVE_URL="${LIVE_URL:-https://dignify-backend-co77gph5gq-uc.a.run.app}"

# 공지 푸시. DB도 앱도 안 쓰니 프록시/bootRun 전에 끝낸다.
# 전체 발송은 회수가 안 되므로 문구를 보여주고 한 번 묻는다. TO로 한 명만 쏠 땐 안 묻는다 —
# 본인 기기 테스트용이라 매번 y를 치게 하면 반복이 성가시다.
if [ "$JOB" = "push" ]; then
    FORCE="${FORCE:-false}"
    echo "제목: $TITLE"
    echo "본문: $BODY"
    [ -n "$MIN_BUILD" ] && echo "대상 빌드: $MIN_BUILD 이상 (그 아래와 빌드 미확인 기기는 빠집니다)"
    if [ -n "$TO" ]; then
        echo "대상: userId=$TO (이 유저 기기에만, 시간대 무시)"
    else
        [ "$FORCE" = "true" ] && echo "(FORCE=true — 로컬 새벽인 유저에게도 나갑니다)"
        if ! confirm "전체 유저에게 발송할까요?"; then
            echo "[cron] 취소했습니다."
            exit 0
        fi
    fi

    RESP=$(curl -s -w "\n%{http_code}" -X POST "$LIVE_URL/internal/push/broadcast" \
        -H "X-Cron-Secret: $CRON_SECRET" -H "Content-Type: application/json" \
        -d "$(jq -nc --arg t "$TITLE" --arg b "$BODY" --argjson f "$FORCE" \
                --argjson u "${TO:-null}" --argjson mb "${MIN_BUILD:-null}" \
                '{title:$t,body:$b,force:$f,userId:$u,minBuild:$mb}')")
    CODE=$(echo "$RESP" | tail -1)
    SENT=$(echo "$RESP" | sed '$d')
    if [ "$CODE" != "200" ]; then
        echo "[cron] 발송 실패($CODE): $SENT"
        exit 1
    fi
    echo "[cron] $SENT대에 발송했습니다."
    if [ "$SENT" = "0" ]; then
        if [ -n "$TO" ]; then
            echo "[cron] userId=$TO 로 등록된 기기 토큰이 없습니다. 목록: ./run-cron.sh push-users"
        elif [ -n "$MIN_BUILD" ]; then
            echo "[cron] 빌드 $MIN_BUILD 이상인 기기가 없거나 전부 로컬 새벽입니다. 빌드별 기기 수: ./run-cron.sh push-users"
        else
            echo "[cron] 등록된 토큰이 없거나 전부 로컬 새벽입니다. 시간 무시하려면 FORCE=true."
        fi
    fi
    exit 0
fi

# 로컬 psql → 프록시 → Cloud SQL. 계정은 앱이 쓰는 것과 동일(application.properties 기본값).
export PGPASSWORD="${DB_PASSWORD:-dignify}"
PSQL=(psql -w -h localhost -p "$PROXY_PORT" -U "${DB_USERNAME:-dignify}" -d "${DB_NAME:-dignify}")

# 요청 한 건의 "아티스트명|상태". 없는 id면 빈 문자열.
fetch_request() {
    "${PSQL[@]}" -tAF'|' -c "SELECT artist_name, status FROM artist_requests WHERE artist_request_id = $1"
}

cleanup() {
    echo ""
    echo "[cron] Shutting down..."
    [ -n "$BOOT_PID" ] && kill "$BOOT_PID" 2>/dev/null
    [ -n "$PROXY_PID" ] && kill "$PROXY_PID" 2>/dev/null
    [ -n "$CAFFEINATE_PID" ] && kill "$CAFFEINATE_PID" 2>/dev/null
    [ -f "$LOG_FILE" ] && cp "$LOG_FILE" "${LOG_FILE%.log}.last.log"  # 마지막 실행 로그 보존 (디버깅용)
    rm -f "$LOG_FILE"
    echo "[cron] Done."
}
trap cleanup EXIT INT TERM

# 절전 방지 (d=디스플레이 i=idle m=디스크 s=시스템 u=user-active)
# 단, 배터리+뚜껑닫힘 케이스는 못 막음 — 오래 돌릴 땐 전원 꽂고 뚜껑 열어둘 것
# -w $$: 이 스크립트가 어떻게 죽든(SIGKILL 포함) caffeinate도 따라 종료 → 좀비 방지
caffeinate -dimsu -w $$ &
CAFFEINATE_PID=$!
echo "[cron] Caffeinate started (PID $CAFFEINATE_PID, tied to $$)"

# 이전 실행이 비정상 종료돼 남은 프록시 선제 정리 (좀비/포트충돌 방지)
if pgrep -f "cloud-sql-proxy.*--port=$PROXY_PORT" >/dev/null; then
    echo "[cron] Killing stale proxy on port $PROXY_PORT..."
    pkill -f "cloud-sql-proxy.*--port=$PROXY_PORT"
    sleep 1
fi

# Cloud SQL Auth Proxy 시작
echo "[cron] Starting Cloud SQL Auth Proxy on port $PROXY_PORT..."
cloud-sql-proxy "$CLOUD_SQL_INSTANCE" --port="$PROXY_PORT" &
PROXY_PID=$!
sleep 3

if ! kill -0 "$PROXY_PID" 2>/dev/null; then
    echo "[cron] ERROR: Cloud SQL Auth Proxy failed to start."
    exit 1
fi
echo "[cron] Proxy running (PID $PROXY_PID)"

# push의 TO에 넣을 userId를 찾는 용도. 토큰이 있는 유저만 나온다 — 없으면 쏴봐야 안 간다.
if [ "$JOB" = "push-users" ]; then
    "${PSQL[@]}" -c "SELECT u.user_id, u.nickname, COUNT(*) AS devices,
                            STRING_AGG(DISTINCT COALESCE(t.app_build::text, '(미확인)'), ', ') AS builds,
                            STRING_AGG(DISTINCT COALESCE(t.time_zone, '(없음)'), ', ') AS time_zones,
                            STRING_AGG(DISTINCT t.environment, ', ') AS envs
                     FROM user_device_tokens t JOIN users u ON u.user_id = t.user_id
                     GROUP BY u.user_id, u.nickname ORDER BY u.user_id"
    # MIN_BUILD를 걸기 전에 몇 대나 남는지 보라고. 배포 직후엔 최신 빌드가 몇 대 안 된다.
    "${PSQL[@]}" -c "SELECT COALESCE(app_build::text, '(미확인)') AS build, COUNT(*) AS devices
                     FROM user_device_tokens GROUP BY app_build ORDER BY app_build DESC NULLS LAST"
    echo "[cron] 본인 기기로만 쏘려면: TO=<user_id> ./run-cron.sh push \"제목\" \"본문\""
    echo "[cron] 특정 빌드 이상만: MIN_BUILD=12 ./run-cron.sh push \"제목\" \"본문\"   # 12=1.0.6"
    exit 0
fi

# 큐레이션 세트 편집. 손으로 고른 id를 넣는 일이라 앱도 API도 필요 없어 bootRun 전에 끝낸다.
if [ "$JOB" = "curate" ]; then
    show_set() {
        "${PSQL[@]}" -c "SELECT c.priority, t.track_id, t.artist_name, t.track_name
                         FROM curation_tracks c JOIN tracks t ON t.track_id = c.track_id
                         WHERE c.is_active ORDER BY c.priority DESC, c.curation_track_id"
    }

    if [ ${#TRACK_IDS[@]} -eq 0 ]; then
        show_set
        echo "[cron] 넣으려면: ./run-cron.sh curate <trackId> [<trackId> ...]  (적은 순서대로 노출)"
        exit 0
    fi

    IDS_CSV=$(IFS=,; echo "${TRACK_IDS[*]}")

    # 넣기 전에 뭘 넣는지 보여준다. id를 손으로 옮겨적는 이상 한 자리 틀리면 엉뚱한 곡이 대문에 걸린다.
    # 없는 id는 아래 INSERT가 FK로 막지만, 막히는 것보다 미리 보이는 편이 고치기 쉽다.
    "${PSQL[@]}" -c "SELECT v.id, t.artist_name, t.track_name,
                            CASE WHEN t.track_id IS NULL THEN '없는 id'
                                 WHEN NOT t.is_active THEN '비활성 트랙'
                                 ELSE 'ok' END AS state
                     FROM unnest(ARRAY[$IDS_CSV]) WITH ORDINALITY AS v(id, ord)
                     LEFT JOIN tracks t ON t.track_id = v.id ORDER BY v.ord"

    [ "$REPLACE" = "true" ] && echo "(REPLACE=true — 지금 세트에 있는 나머지 곡은 전부 꺼집니다)"
    if ! confirm "이 순서로 세트에 넣을까요?"; then
        echo "[cron] 취소했습니다."
        exit 0
    fi

    # 적은 순서대로 앞에 나오게 priority를 역순으로 매긴다 (세트는 priority DESC 정렬).
    VALUES=""
    for i in "${!TRACK_IDS[@]}"; do
        VALUES+="(${TRACK_IDS[$i]},$(( ${#TRACK_IDS[@]} - i ))),"
    done

    # 끄기와 넣기가 한 -c 안에 있어야 한 트랜잭션으로 돈다 — 중간에 끊겨 빈 세트가 나가면 안 된다.
    SQL=""
    [ "$REPLACE" = "true" ] && SQL+="UPDATE curation_tracks SET is_active = FALSE, updated_at = NOW()
                                     WHERE is_active AND track_id <> ALL(ARRAY[$IDS_CSV]);"
    SQL+="INSERT INTO curation_tracks (track_id, priority, is_active, created_at, updated_at)
          SELECT v.id, v.prio, TRUE, NOW(), NOW() FROM (VALUES ${VALUES%,}) AS v(id, prio)
          ON CONFLICT (track_id) DO UPDATE
            SET priority = EXCLUDED.priority, is_active = TRUE, updated_at = NOW();"

    if ! "${PSQL[@]}" -v ON_ERROR_STOP=1 -q -c "$SQL"; then
        echo "[cron] 실패했습니다. 세트는 그대로입니다."
        exit 1
    fi

    echo "[cron] 지금 나가는 세트:"
    show_set
    exit 0
fi

# id 없이 부른 resolve-artist는 목록 조회만 한다. 앱이 필요 없으니 bootRun(약 1분) 전에 끝낸다.
if [ "$JOB" = "resolve-artist" ] && [ ${#IDS[@]} -eq 0 ]; then
    "${PSQL[@]}" -c "SELECT ar.artist_request_id AS id, ar.artist_name, u.nickname, ar.created_at
                     FROM artist_requests ar JOIN users u ON u.user_id = ar.user_id
                     WHERE ar.status = 'PENDING' ORDER BY ar.artist_request_id"
    echo "[cron] 처리하려면: ./run-cron.sh resolve-artist <id> [<id> ...]"
    echo "[cron] 거절하려면: ./run-cron.sh cancel-artist <id> [<id> ...]"
    exit 0
fi

# 상태 변경만 하는 두 작업. 곡 수집은 collect-artist로 따로 돌린 뒤 부르는 것이므로 앱을 안 띄운다.
if [ "$JOB" = "resolve-artist" ] || [ "$JOB" = "cancel-artist" ]; then
    if [ "$JOB" = "resolve-artist" ]; then
        NEW_STATUS="ADDED"
        REQ_BODY='{"status":"ADDED"}'
    else
        REASON="${REASON:-Not available on Apple Music}"
        NEW_STATUS="CANCELED"
        REQ_BODY=$(jq -nc --arg r "$REASON" '{status:"CANCELED",cancelReason:$r}')
    fi

    for id in "${IDS[@]}"; do
        ROW=$(fetch_request "$id")
        if [ -z "$ROW" ]; then
            echo "[cron] #$id: 그런 요청이 없습니다. 건너뜁니다."
            continue
        fi
        ARTIST="${ROW%%|*}"
        STATUS="${ROW##*|}"
        if [ "$STATUS" != "PENDING" ]; then
            echo "[cron] #$id '$ARTIST': 이미 $STATUS 상태입니다. 건너뜁니다."
            continue
        fi

        # 막지는 않고 개수만 알려준다. 이름이 조금 달라도(NCT vs NCT 127) 0으로 나올 수 있어서 판단은 사람이.
        if [ "$NEW_STATUS" = "ADDED" ]; then
            SQL_NAME="${ARTIST//\'/\'\'}"
            TOTAL=$("${PSQL[@]}" -tAc "SELECT COUNT(*) FROM tracks WHERE is_active AND artist_name ILIKE '%${SQL_NAME}%'")
            echo "[cron] #$id '$ARTIST': 피드에 총 $TOTAL곡"
        fi

        CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
            "$LIVE_URL/internal/artist-requests/$id/resolve" \
            -H "X-Cron-Secret: $CRON_SECRET" -H "Content-Type: application/json" \
            -d "$REQ_BODY")
        if [ "$CODE" != "200" ]; then
            echo "[cron] #$id '$ARTIST' → $NEW_STATUS 처리 실패($CODE)"
        elif [ "$NEW_STATUS" = "ADDED" ]; then
            echo "[cron] #$id '$ARTIST' → ADDED 처리 완료, 요청한 유저에게 푸시 발송"
        else
            echo "[cron] #$id '$ARTIST' → 거절 처리 완료 (사유: $REASON)"
        fi
    done
    exit 0
fi

# Spring Boot 시작
echo "[cron] Starting Spring Boot..."
DB_PORT=$PROXY_PORT ./gradlew bootRun > "$LOG_FILE" 2>&1 &
BOOT_PID=$!

# Spring Boot ready 대기 (최대 120초)
echo "[cron] Waiting for Spring Boot to be ready..."
for i in $(seq 1 60); do
    if grep -q "Started DignifyApplication" "$LOG_FILE" 2>/dev/null; then
        echo "[cron] Spring Boot is ready."
        break
    fi
    if ! kill -0 "$BOOT_PID" 2>/dev/null; then
        echo "[cron] ERROR: Spring Boot process died. Last log:"
        tail -20 "$LOG_FILE"
        exit 1
    fi
    if [ "$i" -eq 60 ]; then
        echo "[cron] ERROR: Timed out waiting for Spring Boot."
        exit 1
    fi
    sleep 2
done

# 크론잡 트리거
if [ "$JOB" = "collect-artist" ] || [ "$JOB" = "collect-artist-id" ]; then
    # 두 잡은 파라미터만 다르고 흐름이 같다. 엔드포인트 경로는 잡 이름과 동일.
    if [ "$JOB" = "collect-artist" ]; then PARAM="name"; else PARAM="artistId"; fi
    # 앱 쪽 진행 로그(resolving/found/ABORTED/skipped)를 콘솔로 흘려줌. 루프 끝나면 정리.
    tail -n 0 -f "$LOG_FILE" | grep --line-buffered -E "collect-artist|Skipping track|WARN|ERROR|Exception|Caused by|^[[:space:]]+at " &
    TAIL_PID=$!
    # 아티스트 목록을 순회하며 동기 검색-적재. 각 호출은 저장 개수(200 OK 본문)를 반환.
    for artist in "${ARTISTS[@]}"; do
        echo "[cron] Collecting artist: $artist"
        BODY=$(curl -s -w "\n%{http_code}" -X POST \
            "http://localhost:$APP_PORT/internal/cron/$JOB" \
            --data-urlencode "$PARAM=$artist" \
            -H "X-Cron-Secret: $CRON_SECRET")
        CODE=$(echo "$BODY" | tail -1)
        SAVED=$(echo "$BODY" | sed '$d')
        if [ "$CODE" != "200" ]; then
            echo "[cron] ERROR ($CODE) for '$artist': $SAVED"
        else
            echo "[cron] '$artist' → saved $SAVED tracks"
        fi
        sleep 1
    done
    sleep 1  # 마지막 앱 로그가 콘솔로 흘러나올 시간
    kill "$TAIL_PID" 2>/dev/null
    echo "[cron] collect-artist finished."
    exit 0
fi

if [ "$JOB" = "collect" ]; then
    CRON_URL="http://localhost:$APP_PORT/internal/cron/collect?endIndex=$END_INDEX"
else
    CRON_URL="http://localhost:$APP_PORT/internal/cron/enrich-ko"
fi
echo "[cron] Triggering cron job '$JOB'..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$CRON_URL" \
    -H "X-Cron-Secret: $CRON_SECRET")

if [ "$RESPONSE" != "202" ]; then
    echo "[cron] ERROR: Unexpected response: $RESPONSE"
    exit 1
fi
echo "[cron] Cron job started (202 Accepted). Tailing logs..."
echo "[cron] Press Ctrl+C when cron job completes."
echo ""

# 완료 로그 스트리밍
tail -f "$LOG_FILE" | grep --line-buffered -iE "batch|finished|drained|WARN|ERROR"
