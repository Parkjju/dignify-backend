-- 무드 추천 벡터. PCA 32차원, L2 정규화돼 있어 내적이 곧 코사인이다.
-- 엔티티가 없어서(32컬럼짜리를 매핑만 위해 두는 건 낭비) ddl-auto가 못 만든다. 여기서 만든다.
-- 채우는 건 mood-pilot/load-vectors.sh. 컬럼 수는 그 스크립트의 DIMS, MoodRecommender.DIMS와 같아야 한다.
CREATE TABLE IF NOT EXISTS track_vectors (
    track_id BIGINT PRIMARY KEY REFERENCES tracks(track_id) ON DELETE CASCADE,
    v0 REAL NOT NULL,
    v1 REAL NOT NULL,
    v2 REAL NOT NULL,
    v3 REAL NOT NULL,
    v4 REAL NOT NULL,
    v5 REAL NOT NULL,
    v6 REAL NOT NULL,
    v7 REAL NOT NULL,
    v8 REAL NOT NULL,
    v9 REAL NOT NULL,
    v10 REAL NOT NULL,
    v11 REAL NOT NULL,
    v12 REAL NOT NULL,
    v13 REAL NOT NULL,
    v14 REAL NOT NULL,
    v15 REAL NOT NULL,
    v16 REAL NOT NULL,
    v17 REAL NOT NULL,
    v18 REAL NOT NULL,
    v19 REAL NOT NULL,
    v20 REAL NOT NULL,
    v21 REAL NOT NULL,
    v22 REAL NOT NULL,
    v23 REAL NOT NULL,
    v24 REAL NOT NULL,
    v25 REAL NOT NULL,
    v26 REAL NOT NULL,
    v27 REAL NOT NULL,
    v28 REAL NOT NULL,
    v29 REAL NOT NULL,
    v30 REAL NOT NULL,
    v31 REAL NOT NULL,
    -- tracks.genre_id 복제본. 장르 필터를 내적 계산 전에 걸려면 이 테이블에 있어야 한다 —
    -- tracks 조인은 8만5천 행을 다 곱한 뒤에 거르므로 +67ms다.
    -- genre_id는 수집 시점에 정해지고 이후 안 바뀌므로 복제해도 어긋날 위험이 낮다.
    -- 채우는 것도 load-vectors.sh다(tracks에서 복사). FK는 일부러 안 건다 — 파생 사본이다.
    genre_id BIGINT
);

-- 이미 만들어진 테이블(운영·로컬)에 컬럼을 더한다. 새로 만든 경우엔 위에서 이미 생겨 no-op.
ALTER TABLE track_vectors ADD COLUMN IF NOT EXISTS genre_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_track_vectors_genre ON track_vectors (genre_id);
