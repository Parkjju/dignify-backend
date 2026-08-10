#!/bin/bash
set -e

usage() {
    cat <<'EOF'
latency.sh — 엔드포인트별 응답시간 분포를 Cloud Run 로그에서 뽑는다

Usage:
  ./latency.sh                          최근 1일
  ./latency.sh 7d                       최근 7일 (s/m/h/d 단위)
  ./latency.sh 3d 2026-08-10T06:03:19Z  3일치를 그 시각 기준 전/후로 갈라 비교
  ./latency.sh -h | --help              이 도움말

Examples:
  ./latency.sh 6h
  ./latency.sh 2d "$(gcloud run revisions describe dignify-backend-00044-f44 \
                       --region us-central1 --format='value(metadata.creationTimestamp)')"

두 번째 인자를 주면 배포 전후 비교 모드가 된다. 고친 게 실제로 나아졌는지 볼 때 쓴다.
리비전 생성 시각을 그대로 넣으면 되고, 배포 직후엔 표본이 적으니 건수 칼럼을 같이 볼 것.

앱에는 계측 코드가 한 줄도 없다 — Cloud Run이 모든 요청에 대해 남기는 httpRequest 로그만
읽는다. 그래서 과거 구간도 소급해서 볼 수 있다. 대신 서버 안에서 어디에 시간을 썼는지는
안 나온다. 그건 Cloud SQL Query Insights(쿼리별) 또는 hibernate.generate_statistics(쿼리 수)로.

숫자 읽는 법: p50은 평상시 체감, p95는 느린 축 체감, max는 최악 한 건이다. max만 튀는 건
콜드스타트나 일회성일 때가 많으니 p95를 기준으로 볼 것.
EOF
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
esac

FRESHNESS="${1:-1d}"
CUT="${2:-}"

RAW=$(mktemp)
trap 'rm -f "$RAW"' EXIT

echo "[latency] Cloud Run 로그 읽는 중 (최근 $FRESHNESS)..." >&2
gcloud logging read \
    'resource.type="cloud_run_revision" AND resource.labels.service_name="dignify-backend" AND httpRequest.requestMethod!=""' \
    --freshness="$FRESHNESS" --limit=100000 \
    --format='csv[no-heading](timestamp,httpRequest.requestMethod,httpRequest.requestUrl,httpRequest.status,httpRequest.latency)' \
    > "$RAW"

python3 - "$RAW" "$CUT" <<'PY'
import sys, csv, re, collections
from datetime import datetime

raw, cut = sys.argv[1], sys.argv[2]
CUT = datetime.fromisoformat(cut.replace('Z', '+00:00')) if cut else None

def norm(url):
    # /tracks/81736 처럼 경로에 박힌 id를 뭉쳐야 엔드포인트 단위로 집계된다
    return re.sub(r'/\d+', '/{id}', re.sub(r'^https?://[^/]+', '', url).split('?')[0])

def pct(vals, q):
    v = sorted(vals)
    return v[min(int(len(v) * q), len(v) - 1)]

pre = collections.defaultdict(list)
post = collections.defaultdict(list)
status = collections.Counter()
allv = []
for row in csv.reader(open(raw)):
    if len(row) < 5:
        continue
    ts, method, url, code, lat = row
    ms = float(lat.rstrip('s')) * 1000
    key = f'{method} {norm(url)}'
    status[code] += 1
    allv.append(ms)
    bucket = post if (CUT and datetime.fromisoformat(ts.replace('Z', '+00:00')) >= CUT) else pre
    bucket[key].append(ms)

if not allv:
    print('데이터 없음 — 기간을 늘려보거나 서비스명을 확인할 것')
    sys.exit()

print(f'\n총 {len(allv)}건 | 전체 p50={pct(allv,.5):.0f}ms p95={pct(allv,.95):.0f}ms p99={pct(allv,.99):.0f}ms')
print('상태코드:', dict(status.most_common()))
err = sum(n for c, n in status.items() if c.startswith('5'))
if err:
    print(f'⚠️  5xx {err}건 — 원인은 gcloud logging read 로 httpRequest.status>=500 조회할 것')

if CUT:
    print(f'\n기준 시각: {CUT.isoformat()}\n')
    print(f'{"엔드포인트":38} {"전 p95":>10} {"후 p95":>10}   건수(전/후)')
    keys = sorted(set(pre) | set(post),
                  key=lambda k: -(pct(post[k], .95) if post.get(k) else 0))
    for k in keys:
        a = f'{pct(pre[k],.95):.0f}ms' if pre.get(k) else '-'
        b = f'{pct(post[k],.95):.0f}ms' if post.get(k) else '-'
        print(f'{k:38} {a:>10} {b:>10}   {len(pre.get(k,[]))}/{len(post.get(k,[]))}')
else:
    print('\n     건수      p50      p95       max  엔드포인트')
    for k, v in sorted(pre.items(), key=lambda x: -pct(x[1], .95)):
        print(f'{len(v):7d} {pct(v,.5):7.0f}ms {pct(v,.95):7.0f}ms {max(v):8.0f}ms  {k}')
PY
