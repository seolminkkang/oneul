// ===============================
// 좋아요 동시 부하 측정 (k6)
//
// 재는 것:
//   인기 피드 1개에 좋아요가 몰릴 때
//     (1) 좋아요 요청이 얼마나 버티나  — hot row 경합
//     (2) 홈 조회가 같이 나빠지나
//     (3) 정렬 인덱스(idx_feed_status_like_created)가 있고 없고의 차이
//     (4) 카운터(feed.like_count)가 실제 기록(feed_like)과 어긋나는가
//
// ★ 왜 한 피드에 몰아넣나
//   like_count 를 올리는 UPDATE 는 그 피드의 행 하나를 잠근다.
//   좋아요가 흩어지면 경합이 안 생긴다. 실제로는 인기 피드에 몰리므로
//   "한 피드 집중"이 실제이자 최악의 조건이다.
//
// ★ 왜 토글인가
//   UNIQUE(feed_id, user_id) 때문에 한 사용자는 한 번만 누를 수 있다.
//   토글이면 같은 사용자가 눌렀다 뗐다 반복할 수 있어 부하를 계속 유지할 수 있고,
//   프론트 하트 버튼의 실제 동작과도 같다.
//
// 규모 근거:
//   홈 조회 50/s = 전체 400/s ÷ 1인당 8요청 (브라우저 실측)
//   좋아요 25/s  = 홈 들어온 50명 중 절반이 1개 누름 (가정)
//                  → 가정이므로 25/100/300 으로 밀어 여유를 본다
//
// 실행:
//   k6 run -e LIKE_RATE=25 -e TARGET_FEED_ID=998642 like-load-test.js
// ===============================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const TARGET_FEED_ID = __ENV.TARGET_FEED_ID || '998642';  // 커뮤니티 피드 최상단
const HOME_RATE = Number(__ENV.HOME_RATE || 50);
const LIKE_RATE = Number(__ENV.LIKE_RATE || 25);
const DURATION = __ENV.DURATION || '30s';
const TOKEN_COUNT = Number(__ENV.TOKEN_COUNT || 60);

const tHome = new Trend('api_home_community', true);
const tLike = new Trend('api_like_toggle', true);
const errHome = new Rate('err_home');
const errLike = new Rate('err_like');
// 실패의 정체를 가른다. 0 = 응답 자체를 못 받음(연결 문제), 4xx/5xx = 서버가 답한 실패
const likeStatus0 = new Counter('like_status_0');
const likeStatus4xx = new Counter('like_status_4xx');
const likeStatus5xx = new Counter('like_status_5xx');

export const options = {
  scenarios: {
    home: {
      executor: 'constant-arrival-rate',
      rate: HOME_RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 50, maxVUs: 300, exec: 'home',
    },
    like: {
      executor: 'constant-arrival-rate',
      rate: LIKE_RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 50, maxVUs: 300, exec: 'like',
    },
  },
  thresholds: { 'err_home': ['rate<1'], 'err_like': ['rate<1'] },
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
  if (tokens.length === 0) throw new Error('토큰을 하나도 못 받았다');
  console.log(`토큰 ${tokens.length}개 / 홈 ${HOME_RATE}/s / 좋아요 ${LIKE_RATE}/s / 대상 피드 ${TARGET_FEED_ID}`);
  return { tokens };
}

export function home() {
  const res = http.get(`${BASE}/api/feeds/community`);
  tHome.add(res.timings.duration);
  errHome.add(res.status !== 200);
  check(res, { '홈 200': (r) => r.status === 200 });
}

export function like(data) {
  // VU 마다 고정 토큰 — 같은 사용자에게 동시 요청이 겹치지 않게 한다.
  // (겹치면 UNIQUE 위반이 나는데, 그건 이번에 재려는 경합이 아니다)
  const token = data.tokens[__VU % data.tokens.length];
  const params = { headers: { Authorization: `Bearer ${token}` } };

  const res = http.post(`${BASE}/api/feeds/${TARGET_FEED_ID}/like`, null, params);
  tLike.add(res.timings.duration);
  errLike.add(res.status !== 200);
  if (res.status === 0) {
    likeStatus0.add(1);
    if (__ITER === 0) console.log(`status 0 — ${res.error_code} ${res.error}`);
  } else if (res.status >= 500) {
    likeStatus5xx.add(1);
    if (__ITER === 0) console.log(`status ${res.status} — ${String(res.body).slice(0, 300)}`);
  } else if (res.status >= 400) {
    likeStatus4xx.add(1);
    if (__ITER === 0) console.log(`status ${res.status} — ${String(res.body).slice(0, 200)}`);
  }
  check(res, { '좋아요 200': (r) => r.status === 200 });
}
