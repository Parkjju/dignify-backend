#!/bin/bash
# probe-latency.sh — 이 기기에서 서버까지 왕복이 얼마나 걸리는지 잰다.
#
# 왜 필요한가: latency.sh가 읽는 Cloud Run 로그의 latency는 "서버 안에서 쓴 시간"뿐이라
# 접속·TLS·왕복 네트워크가 통째로 빠져 있다(실측 예: 서버 로그 21.8ms / 실제 체감 187ms).
# 서버가 us-central1(아이오와)에 있어서 이 값은 재는 위치에 따라 완전히 달라진다.
# 한국 유저가 실제로 얼마나 기다리는지는 한국에서 재야만 알 수 있다.
#
# 사용법:  ./probe-latency.sh          (10회)
#          ./probe-latency.sh 30       (30회, 더 안정적)
#
# 로그인이 필요 없는 경로만 쓰므로 계정도 토큰도 필요 없다.
# curl만 있으면 되고 gawk 같은 추가 도구는 안 쓴다(맥 기본 상태에서 그대로 동작).
# 출력 전체를 복사해서 보내주면 된다.

set -u
URL="https://dignify-backend-co77gph5gq-uc.a.run.app/feed/curation"
N="${1:-10}"

command -v curl >/dev/null 2>&1 || { echo "curl이 없습니다."; exit 1; }

TMP=$(mktemp); TMP2=$(mktemp)
trap 'rm -f "$TMP" "$TMP2"' EXIT

# 숫자를 stdin으로 받아 최소/중앙/최대를 찍는다. sort -n만 써서 어느 awk에서나 돈다.
stats() {
    sort -n | awk -v label="$1" '
        {v[NR]=$1}
        END{
            if(NR==0){printf "  %-22s 측정 실패\n", label; exit}
            m=int((NR+1)/2)
            printf "  %-22s 중앙 %5.0fms   (최소 %.0f / 최대 %.0f)\n", label, v[m], v[1], v[NR]
        }'
}

echo "════════ dignify 서버 응답 측정 ════════"
echo "대상   : $URL"
echo "위치   : $(curl -s --max-time 5 https://ipinfo.io/city 2>/dev/null || echo '?'), $(curl -s --max-time 5 https://ipinfo.io/country 2>/dev/null || echo '?')"
echo "회선   : $(curl -s --max-time 5 https://ipinfo.io/org 2>/dev/null || echo '?')"
echo "시각   : $(date '+%Y-%m-%d %H:%M:%S %Z')"
echo "횟수   : $N"
echo

echo "[1] 새로 접속할 때 — 앱을 처음 켤 때 겪는 값"
: > "$TMP"
for _ in $(seq "$N"); do
    curl -s -o /dev/null -w "%{time_connect} %{time_appconnect} %{time_starttransfer} %{time_total}\n" "$URL" >> "$TMP"
done
awk '{print $1*1000}' "$TMP" | stats "TCP 접속(왕복 1회)"
awk '{print $2*1000}' "$TMP" | stats "TLS 협상 완료까지"
awk '{print $3*1000}' "$TMP" | stats "첫 바이트까지"
awk '{print $4*1000}' "$TMP" | stats "전체"

echo
echo "[2] 연결을 재사용할 때 — 앱을 쓰는 중에 겪는 값"
: > "$TMP2"
for _ in $(seq "$N"); do
    curl -s "$URL" -o /dev/null -o /dev/null -w "%{time_total}\n" "$URL" >> "$TMP2"
done
awk '{print $1*1000}' "$TMP2" | stats "전체"

echo
echo "참고: 이 중 서버가 실제로 일한 시간은 20~200ms이고, 나머지가 거리에서 오는 값이다."
echo "════════════════════════════════════════"
