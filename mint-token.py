#!/usr/bin/env python3
"""로컬 테스트용 액세스 토큰 발급. 운영 시크릿과 다르므로 로컬 서버에만 통한다.

    ./mint-token.py 37

인증 필터가 서명과 sub(userId)만 검증하고 user_tokens를 안 본다 — 그래서 로그인 없이도 된다.
"""
import base64, hashlib, hmac, json, os, sys, time

secret = next(l.split('=', 1)[1].strip() for l in open('.env') if l.startswith('JWT_SECRET='))
user_id = sys.argv[1]
b64 = lambda raw: base64.urlsafe_b64encode(raw).rstrip(b'=').decode()
now = int(time.time())
head = b64(json.dumps({"alg": "HS256"}, separators=(',', ':')).encode())
body = b64(json.dumps({"sub": str(user_id), "iat": now, "exp": now + 3600}, separators=(',', ':')).encode())
signing_input = f"{head}.{body}".encode()
sig = b64(hmac.new(secret.encode(), signing_input, hashlib.sha256).digest())
print(f"{head}.{body}.{sig}")
