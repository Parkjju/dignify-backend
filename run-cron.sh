#!/bin/bash
set -e

if [ -z "$1" ]; then
    echo "Usage: ./run-cron.sh <endIndex>"
    echo "Example: ./run-cron.sh 50000000"
    exit 1
fi
END_INDEX="$1"

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
    rm -f "$LOG_FILE"
    echo "[cron] Done."
}
trap cleanup EXIT INT TERM

# 절전 방지
caffeinate -s &
CAFFEINATE_PID=$!
echo "[cron] Caffeinate started (PID $CAFFEINATE_PID)"

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
echo "[cron] Triggering cron job..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "http://localhost:$APP_PORT/internal/cron/collect?endIndex=$END_INDEX" \
    -H "X-Cron-Secret: $CRON_SECRET")

if [ "$RESPONSE" != "202" ]; then
    echo "[cron] ERROR: Unexpected response: $RESPONSE"
    exit 1
fi
echo "[cron] Cron job started (202 Accepted). Tailing logs..."
echo "[cron] Press Ctrl+C when cron job completes."
echo ""

# 완료 로그 스트리밍
tail -f "$LOG_FILE" | grep --line-buffered -E "Batch|finished|WARN|ERROR"
