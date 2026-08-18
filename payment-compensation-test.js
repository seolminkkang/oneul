// ===============================
// 보상 트랜잭션 측정 (k6)
//
// 재현하는 상황:
//   인기 챌린지 모집이 열리는 순간 정원 100명이 한꺼번에 참가비를 결제한다.
//   그 중 내부 저장이 실패하면 — 토스에는 돈이 들어와 있고 우리 DB 에는 기록이 없다.
//
// 재는 것:
//   보상 흐름 OFF → **미환불 __건**   (Before)
//   보상 흐름 ON  → **0건**           (After)
//
// ★ "미환불"을 왜 스텁이 세는가
//   보상이 없으면 트랜잭션이 롤백돼 우리 DB 에는 아무 기록도 없다. 그래서 DB 로는 셀 수 없다.
//   스텁이 "승인됐고 아직 취소되지 않은 결제" 집합을 들고 있고, 그 크기가 미환불 건수다.
//   GET /internal/payment-stub/state 로 읽는다.
//
// ★ 사용자 1명당 1건이다
//   challenge_user 의 UNIQUE(challenge_id, user_id) 때문에 같은 사용자는 두 번 참가할 수 없다.
//   그래서 VU 마다 고유 사용자를 쓰고, 목표 건수만큼만 보낸다 (iterations).
//
// 실행:
//   k6 run -e CHALLENGE_ID=2154 -e TOTAL=100 payment-compensation-test.js
// ===============================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const CHALLENGE_ID = __ENV.CHALLENGE_ID;
const TOTAL = Number(__ENV.TOTAL || 100);      // 정원 = 동시 결제 건수
const VUS = Number(__ENV.VUS || 100);          // 한꺼번에 몰리는 상황이므로 TOTAL 과 같게

const tConfirm = new Trend('api_payment_confirm', true);
const errConfirm = new Rate('err_confirm');
const cSuccess = new Counter('result_success');       // 정상 저장
const cRefunded = new Counter('result_refunded');     // 보상으로 자동 환불됨
const cPending = new Counter('result_pending');       // 환불 대기 (보상 실패 또는 OFF)
const cHttpFail = new Counter('result_http_fail');

export const options = {
  scenarios: {
    rush: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,            // VU 하나가 1건만. 사용자당 1건이므로
      maxDuration: '120s',
    },
  },
  thresholds: { 'err_confirm': ['rate<1'] },
};

export function setup() {
  if (!CHALLENGE_ID) {
    throw new Error('CHALLENGE_ID 를 넘겨야 한다. seed-payment.sql 이 출력하는 값');
  }

  // 사용자별로 토큰 + 결제 세션(orderId)을 미리 만든다.
  // 측정 중에 로그인·세션 생성이 섞이면 그 비용이 p95 에 들어간다.
  const users = [];
  for (let i = 0; i < TOTAL; i++) {
    const guestNo = 101 + i;

    http.get(`${BASE}/api/users/guest-login/${guestNo}`, { redirects: 0 });
    const jar = http.cookieJar();
    const cookies = jar.cookiesForURL(`${BASE}/`);
    if (!cookies.accessToken || cookies.accessToken.length === 0) continue;
    const token = cookies.accessToken[0];

    // 결제 세션 생성 → orderId, amount 를 받는다 (Redis 에 15분 TTL 로 저장된다)
    const orderRes = http.get(`${BASE}/api/payments/order/${CHALLENGE_ID}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (orderRes.status !== 200) {
      if (i === 0) console.log(`order 실패 ${orderRes.status}: ${String(orderRes.body).slice(0, 200)}`);
      continue;
    }
    const order = orderRes.json();
    users.push({ token, orderId: order.orderId, amount: order.amount });
  }

  if (users.length === 0) throw new Error('결제 세션을 하나도 못 만들었다');
  console.log(`결제 세션 ${users.length}개 준비 (목표 ${TOTAL})`);
  return { users };
}

export default function (data) {
  const idx = (__VU - 1) % data.users.length;
  const u = data.users[idx];

  const body = JSON.stringify({
    orderId: u.orderId,
    amount: u.amount,
    challengeId: Number(CHALLENGE_ID),
    // paymentKey 는 매 요청 고유해야 한다 — existsByPaymentKey 검증에 걸리므로
    paymentKey: `stub_pk_${__VU}_${__ITER}_${u.orderId}`,
  });

  const res = http.post(`${BASE}/api/payments/confirm`, body, {
    headers: { Authorization: `Bearer ${u.token}`, 'Content-Type': 'application/json' },
  });

  tConfirm.add(res.timings.duration);
  errConfirm.add(res.status !== 200);

  if (res.status !== 200) {
    cHttpFail.add(1);
    if (__VU === 1) console.log(`confirm ${res.status}: ${String(res.body).slice(0, 250)}`);
    return;
  }

  const status = res.json('status');
  if (status === 'SUCCESS' || status === 'success') cSuccess.add(1);
  else if (String(status).toUpperCase().includes('REFUNDED')) cRefunded.add(1);
  else cPending.add(1);

  check(res, { 'confirm 200': (r) => r.status === 200 });
}
