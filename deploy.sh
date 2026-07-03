#!/usr/bin/env bash
# dignify-backend → Cloud Run 배포. dignify-backend 디렉토리에서 실행.
# 사용법:  ./deploy.sh            # latest 태그
#          ./deploy.sh <커밋해시>  # 롤백 편한 태그 지정
# 전제:    gcloud auth configure-docker us-central1-docker.pkg.dev  (최초 1회)
set -euo pipefail

IMAGE="us-central1-docker.pkg.dev/dignify-501004/dignify/dignify-backend"
REGION="us-central1"
SERVICE="dignify-backend"
TAG="${1:-latest}"

echo "▶ build ($TAG, linux/amd64)"
docker build --platform linux/amd64 -t "$IMAGE:$TAG" .

echo "▶ push"
docker push "$IMAGE:$TAG"

echo "▶ deploy"
gcloud run deploy "$SERVICE" \
  --image "$IMAGE:$TAG" \
  --region "$REGION" \
  --min-instances 1

echo "✓ done: $IMAGE:$TAG"
