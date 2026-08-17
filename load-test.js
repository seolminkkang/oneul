// ===============================
// 환급 배치 부하 측정 (k6)
//
// 재현하는 상황:
//   배치가 어제 끝난 챌린지 3개(3,000건)를 환급하는 동안,
//   사용자들이 진행중 챌린지에서 인증하고 조회한다.
//   이때 인증·조회 API 가 얼마나 느려지는지 잰다.
//
// ★ constant-arrival-rate 를 쓰는 이유 (중요)
//   "요청 -> 응답 대기 -> 다음 요청" 방식으로 재면, 서버가 느려질 때
//   요청을 덜 보내게 되어 느린 요청이 적게 잡힌다. p95 가 실제보다 좋게 나온다.
//   (coordinated omission)
//   constant-arrival-rate 는 서버가 느리든 말든 초당 정해진 수를 계속 보낸다.
//   사용자는 앱이 느리다고 접속을 덜 하지 않으므로 이쪽이 실제에 가깝다.
//
// 실행:
//   k6 run -e LIVE_CHALLENGE_ID=15 -e RATE=30 -e DURATION=60s load-test.js
// ===============================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const LIVE_CHALLENGE_ID = __ENV.LIVE_CHALLENGE_ID;   // seed-load.sql 이 출력하는 값
const RATE = Number(__ENV.RATE || 30);               // 초당 요청 수
const DURATION = __ENV.DURATION || '60s';
const TOKEN_COUNT = Number(__ENV.TOKEN_COUNT || 50); // 미리 로그인해둘 사용자 수

// 이력서에 들어갈 숫자를 API 별로 따로 본다.
const tDetail = new Trend('api_challenge_detail', true);  // 챌린지 상세 조회
const tFeeds  = new Trend('api_feed_list', true);         // 피드 목록 조회
const tCert   = new Trend('api_certify', true);           // 인증(피드 작성)
const errors  = new Rate('api_errors');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 100,
      maxVUs: 1000,  // 서버가 느려지면 VU 를 늘려서라도 초당 RATE 를 유지한다.
                     // 상한이 낮으면 k6 가 먼저 막혀서 서버 한계 대신 도구 한계를 재게 된다
    },
  },
  // 판정 기준이 아니라 참고용. Before 는 당연히 넘을 것이고, 그게 측정 대상이다.
  thresholds: {
    'api_errors': ['rate<1'],
  },
};

// ---------- setup: 토큰을 미리 받아둔다 ----------
// 측정 중에 로그인을 하면 로그인 비용이 p95 에 섞인다. 그래서 미리 받는다.
export function setup() {
  if (!LIVE_CHALLENGE_ID) {
    throw new Error('LIVE_CHALLENGE_ID 를 넘겨야 한다. seed-load.sql 출력의 "진행중 챌린지 ID" 값');
  }

  const tokens = [];
  // 부하 유저는 guest101 ~ guest1100 (seed-load.sql 참고)
  for (let i = 101; i < 101 + TOKEN_COUNT; i++) {
    const res = http.get(`${BASE}/api/users/guest-login/${i}`, { redirects: 0 });
    // guest-login 은 토큰을 쿠키로만 내려주는데,
    // JwtAuthenticationFilter.java:32 는 Authorization 헤더에서 읽는다.
    // 그래서 쿠키에서 꺼내 헤더로 옮겨야 한다.
    const jar = http.cookieJar();
    const cookies = jar.cookiesForURL(`${BASE}/`);
    if (cookies.accessToken && cookies.accessToken.length > 0) {
      tokens.push(cookies.accessToken[0]);
    }
  }

  if (tokens.length === 0) {
    throw new Error('토큰을 하나도 못 받았다. 서버가 떠 있는지, seed-load.sql 을 돌렸는지 확인');
  }
  console.log(`토큰 ${tokens.length}개 확보, 진행중 챌린지 = ${LIVE_CHALLENGE_ID}`);
  return { tokens };
}

// ---------- 실제 부하 ----------
export default function (data) {
  const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
  const params = {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  };

  // 실제 앱 사용 비율에 가깝게 섞는다: 조회가 많고 인증은 가끔.
  const dice = Math.random();

  if (dice < 0.5) {
    // 챌린지 상세 — member_count + success_day + challenge_status 를 함께 읽는 가장 무거운 쿼리
    const res = http.get(`${BASE}/api/challenges/my/${LIVE_CHALLENGE_ID}`, params);
    tDetail.add(res.timings.duration);
    errors.add(res.status !== 200);
    check(res, { '상세 조회 200': (r) => r.status === 200 });

  } else if (dice < 0.85) {
    const res = http.get(`${BASE}/api/challenges/${LIVE_CHALLENGE_ID}/feeds`, params);
    tFeeds.add(res.timings.duration);
    errors.add(res.status !== 200);
    check(res, { '피드 목록 200': (r) => r.status === 200 });

  } else {
    // 인증 = 피드 작성 (쓰기). DB 커넥션을 잡으므로 배치와 자원을 다툰다.
    const body = JSON.stringify({
      content: `부하 인증 ${__VU}-${__ITER}`,
      imageUrl: 'https://example.test/load.png',
    });
    const res = http.post(`${BASE}/api/challenges/${LIVE_CHALLENGE_ID}/feeds`, body, params);
    tCert.add(res.timings.duration);
    errors.add(res.status !== 200);
    check(res, { '인증 200': (r) => r.status === 200 });
  }
}
