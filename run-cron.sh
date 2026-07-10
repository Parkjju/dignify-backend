#!/bin/bash
set -e

usage() {
    cat <<'EOF'
run-cron.sh — Cloud SQL 프록시 + bootRun 띄우고 크론잡 트리거

Usage:
  ./run-cron.sh collect <endIndex>              id 브루트포스로 트랙 수집
  ./run-cron.sh collect-artist "A" "B" ...      아티스트명으로 검색-수집 (여러 명 가능)
  ./run-cron.sh enrich-ko                       한글 로컬라이즈 보강
  ./run-cron.sh -h | --help                     이 도움말

Examples:
  ./run-cron.sh collect 50000000
  ./run-cron.sh collect-artist "Radiohead" "Aphex Twin"
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
    collect-artist)
        shift
        ARTISTS=("$@")
        if [ ${#ARTISTS[@]} -eq 0 ]; then
            echo "Usage: ./run-cron.sh collect-artist \"Radiohead\" \"Aphex Twin\" ..."
            exit 1
        fi
        ;;
    *)
        echo "Unknown job: $JOB (collect | collect-artist | enrich-ko)"
        exit 1
        ;;
esac

set -a && source .env && set +a

PROXY_PORT=5433
APP_PORT=8080
LOG_FILE="/tmp/dignify-bootrun.log"

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
if [ "$JOB" = "collect-artist" ]; then
    # 앱 쪽 진행 로그(searching/found/skipped)를 콘솔로 흘려줌. 루프 끝나면 정리.
    tail -n 0 -f "$LOG_FILE" | grep --line-buffered -E "collect-artist|Skipping track|WARN|ERROR|Exception|Caused by|^[[:space:]]+at " &
    TAIL_PID=$!
    # 아티스트 목록을 순회하며 동기 검색-적재. 각 호출은 저장 개수(200 OK 본문)를 반환.
    for artist in "${ARTISTS[@]}"; do
        echo "[cron] Collecting artist: $artist"
        BODY=$(curl -s -w "\n%{http_code}" -X POST \
            "http://localhost:$APP_PORT/internal/cron/collect-artist" \
            --data-urlencode "name=$artist" \
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
