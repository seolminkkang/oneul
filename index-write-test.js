// ===============================
// 정렬 인덱스가 "쓰기"에 주는 영향 측정 (k6)
//
// 재는 것:
//   idx_feed_status_like_created (check_status, like_count DESC, created_at DESC)
//   이 인덱스가 있을 때와 없을 때, 인증 사진이 몰려 올라오면
//   (1) 인증 등록이 느려지는가  (2) 홈 조회가 같이 느려지는가
//
// ★ 왜 인증 등록이 최악의 조건인가
//   새로 올라오는 인증은 전부 check_status='PENDING', like_count=0 이다.
//   즉 인덱스에서 전부 같은 구역에 들어간다. 흩어지지 않고 한곳에 몰린다.
//   (2026-08-17 이전 측정은 45,000행에 흩어 갱신해서 이 경합이 안 잡혔다)
//
// ★ 좋아요는 재지 않는다 — 이 서비스에 좋아요 기능이 없다 (코드 확인).
//   like_count 는 시드 값 그대로이고 바뀌는 경로가 없다.
//
// 규모 근거:
//   홈 조회 50/s = 전체 부하 400/s ÷ 1인당 8요청 (브라우저 실측)
//   인증 등록    = 1,000명 × 챌린지 30개 ÷ 하루 → 몰릴 때 초당 20건 근처 (기준)
//
// 실행:
//   k6 run -e CERT_RATE=20 -e LIVE_CHALLENGE_ID=1741 index-write-test.js
// ===============================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const LIVE_CHALLENGE_ID = __ENV.LIVE_CHALLENGE_ID || '1741';
const HOME_RATE = Number(__ENV.HOME_RATE || 50);   // 홈 조회 (고정)
const CERT_RATE = Number(__ENV.CERT_RATE || 20);   // 인증 등록 (변수)
const DURATION = __ENV.DURATION || '30s';
const TOKEN_COUNT = Number(__ENV.TOKEN_COUNT || 50);

const tHome = new Trend('api_home_community', true);   // 홈 = 커뮤니티 피드 조회
const tCert = new Trend('api_certify_write', true);    // 인증 등록 (쓰기)
const errHome = new Rate('err_home');
const errCert = new Rate('err_cert');

export const options = {
  scenarios: {
    // 읽기: 인증이 몰리는 동안에도 홈이 멀쩡한지 본다
    home: {
      executor: 'constant-arrival-rate',
      rate: HOME_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 50,
      maxVUs: 500,
      exec: 'home',
    },
    // 쓰기: 인덱스 유지 비용이 걸리는 쪽
    certify: {
      executor: 'constant-arrival-rate',
      rate: CERT_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 50,
      maxVUs: 500,
      exec: 'certify',
    },
  },
  thresholds: { 'err_home': ['rate<1'], 'err_cert': ['rate<1'] },
};

export function setup() {
  const tokens = [];
  for (let i = 101; i < 101 + TOKEN_COUNT; i++) {
    http.get(`${BASE}/api/users/guest-login/${i}`, { redirects: 0 });
    const jar = http.cookieJar();
    const cookies = jar.cookiesForURL(`${BASE}/`);
    if (cookies.accessToken && cookies.accessToken.length > 0) {
      tokens.push(cookies.accessToken[0]);
    }
  }
  if (tokens.length === 0) {
    throw new Error('토큰을 하나도 못 받았다. 서버·시드 확인');
  }
  console.log(`토큰 ${tokens.length}개 / 홈 ${HOME_RATE}/s / 인증 ${CERT_RATE}/s`);
  return { tokens };
}

// 홈 조회 — 인증이 필요 없는 엔드포인트 (SecurityConfig permitAll)
export function home() {
  const res = http.get(`${BASE}/api/feeds/community`);
  tHome.add(res.timings.duration);
  errHome.add(res.status !== 200);
  check(res, { '홈 200': (r) => r.status === 200 });
}

// 인증 등록 — feed INSERT. 인덱스가 있으면 명단에 끼워넣는 비용이 붙는다
export function certify(data) {
  const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
  const params = {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  };
  const body = JSON.stringify({
    content: `부하인증 ${__VU}-${__ITER}`,
    imageUrl: 'https://example.test/load.png',   // presigned 경로를 안 타게 (측정 대상 아님)
  });
  const res = http.post(`${BASE}/api/challenges/${LIVE_CHALLENGE_ID}/feeds`, body, params);
  tCert.add(res.timings.duration);
  errCert.add(res.status !== 200);
  check(res, { '인증 200': (r) => r.status === 200 });
}
