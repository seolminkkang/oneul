// ===============================
// 홈 순위가 실시간으로 바뀌는 동안 홈을 조회한다 (k6)
//
// 재는 것:
//   상위 12개 피드에 좋아요가 계속 들어가 **순위가 매 요청마다 뒤바뀌는 상태**에서
//     (1) 홈 조회가 느려지는가
//     (2) 좋아요가 느려지거나 실패하는가
//     (3) 정렬 인덱스(idx_feed_status_like_created)가 있고 없고의 차이
//     (4) 카운터(feed.like_count)가 실제 기록(feed_like)과 어긋나는가
//
// ★ 왜 상위 12개인가 (이전 시나리오의 실패 원인)
//   like-load-test.js 는 피드 1개에만 좋아요를 몰아넣었고, 게다가 측정 전에
//   그 피드의 like_count 를 0 으로 초기화했다. 상위 12개는 49 였으므로
//   대상이 끝까지 12위 안에 못 들어갔다 → **순위가 전혀 안 변했다.**
//   그건 "한 행에 락이 몰리는 상황"(데드락 발견)이었고 "순위 변동"이 아니었다.
//
// ★ 왜 ±1 로 순위가 크게 흔들리나
//   시드가 like_count 를 결정적 계산으로 넣어서 **899개 피드가 49 로 동점**이다.
//   동점자 중 하나가 +1 되면 곧바로 1위로 올라가고, −1 되면 12위 밖으로 밀려나
//   다른 피드가 그 자리에 들어온다. 즉 홈 조회 결과가 매 요청마다 바뀐다.
//   홈 조회는 인덱스의 **맨 앞 구간**을 읽는데, 쓰기가 흔드는 곳이 정확히 거기다.
//
// 실행:
//   k6 run -e LIKE_RATE=25 like-rank-churn-test.js
// ===============================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
// 측정 시작 시점의 상위 12개. 실행 전 SQL 로 다시 뽑아 넘긴다
const TARGET_IDS = (__ENV.TARGET_FEED_IDS ||
  '999392,1000142,1000892,1001642,1002392,1003142,1003892,1004642,1005392,1006142,1006892,1007642')
  .split(',').map((s) => s.trim());

const HOME_RATE = Number(__ENV.HOME_RATE || 50);
const LIKE_RATE = Number(__ENV.LIKE_RATE || 25);
const DURATION = __ENV.DURATION || '30s';
const TOKEN_COUNT = Number(__ENV.TOKEN_COUNT || 400);

const tHome = new Trend('api_home_community', true);
const tLike = new Trend('api_like_toggle', true);
const errHome = new Rate('err_home');
const errLike = new Rate('err_like');
const likeStatus0 = new Counter('like_status_0');
const likeStatus4xx = new Counter('like_status_4xx');
const likeStatus5xx = new Counter('like_status_5xx');

export const options = {
  scenarios: {
    home: {
      executor: 'constant-arrival-rate',
      rate: HOME_RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 60, maxVUs: 400, exec: 'home',
    },
    like: {
      executor: 'constant-arrival-rate',
      rate: LIKE_RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 60, maxVUs: 400, exec: 'like',
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
  console.log(`토큰 ${tokens.length}개 / 홈 ${HOME_RATE}/s / 좋아요 ${LIKE_RATE}/s / 대상 ${TARGET_IDS.length}개`);
  return { tokens };
}

export function home() {
  const res = http.get(`${BASE}/api/feeds/community`);
  tHome.add(res.timings.duration);
  errHome.add(res.status !== 200);
  check(res, { '홈 200': (r) => r.status === 200 });
}

export function like(data) {
  // 사용자는 VU 마다 고정 — 같은 사용자에게 동시 요청이 겹치지 않게 한다
  // (겹치면 UNIQUE(feed_id,user_id) 위반이 나는데 이번 측정 대상이 아니다)
  const token = data.tokens[__VU % data.tokens.length];
  // 대상 피드는 매 요청마다 바꾼다 — 12개가 골고루 흔들리게
  const feedId = TARGET_IDS[(__VU + __ITER) % TARGET_IDS.length];

  const params = { headers: { Authorization: `Bearer ${token}` } };
  const res = http.post(`${BASE}/api/feeds/${feedId}/like`, null, params);

  tLike.add(res.timings.duration);
  errLike.add(res.status !== 200);
  if (res.status === 0) {
    likeStatus0.add(1);
  } else if (res.status >= 500) {
    likeStatus5xx.add(1);
    if (__ITER === 0) console.log(`5xx — ${String(res.body).slice(0, 160)}`);
  } else if (res.status >= 400) {
    likeStatus4xx.add(1);
    if (__ITER === 0) console.log(`${res.status} — ${String(res.body).slice(0, 160)}`);
  }
  check(res, { '좋아요 200': (r) => r.status === 200 });
}
