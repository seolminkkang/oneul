-- ===============================
-- 보상 트랜잭션 측정용 시드 (2026-08-18)
--
-- 재현하려는 상황:
--   인기 챌린지의 모집이 시작되는 순간, 정원 100명이 한꺼번에 참가비를 결제한다.
--   그 중 일부(또는 전부)의 내부 저장이 실패하면 어떻게 되는가.
--
-- ★ 시작 전 챌린지여야 한다
--   PaymentController.getOrderId 가 이미 시작된 챌린지의 결제 세션을 막는다.
--   (challenge.start_date 가 미래여야 한다)
--
-- ★ 사용자 1명당 1번만 참가할 수 있다
--   challenge_user 의 UNIQUE(challenge_id, user_id) 때문이다.
--   그래서 동시 100건에는 서로 다른 사용자 100명이 필요하다.
--   guest101~ 를 쓴다 (seed-load.sql 이 만든다).
--
-- 몇 번을 돌려도 같은 상태가 된다.
--
-- 실행: docker exec -i -e MYSQL_PWD=1234 oneul-mysql mysql -uroot < seed-payment.sql
-- ===============================

USE oneul;

SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 1. 이전 측정 흔적 제거 ----------
-- 결제 관련 부수 테이블부터 지운다 (FK 순서)
DELETE rr FROM refund_receipt rr
    JOIN challenge c ON rr.challenge_id = c.challenge_id
    WHERE c.name = 'payment-challenge';

DELETE cfl FROM cancel_fail_log cfl
    JOIN challenge c ON cfl.challenge_id = c.challenge_id
    WHERE c.name = 'payment-challenge';

DELETE p FROM payment p
    JOIN challenge c ON p.challenge_id = c.challenge_id
    WHERE c.name = 'payment-challenge';

DELETE cu FROM challenge_user cu
    JOIN challenge c ON cu.challenge_id = c.challenge_id
    WHERE c.name = 'payment-challenge';

DELETE FROM challenge WHERE name = 'payment-challenge';

SET FOREIGN_KEY_CHECKS = 1;

-- ---------- 2. 모집중 챌린지 1개 (시작 전) ----------
INSERT INTO challenge
    (name, owner_id, category_id, description, total_day, goal_day,
     is_challenge, is_public, start_date, end_date, entry_fee, challenge_status, member_count)
VALUES
    ('payment-challenge', 1, 1, '보상 트랜잭션 측정용 모집중 챌린지',
     30, 20, TRUE, TRUE,
     DATE_ADD(CURDATE(), INTERVAL 3 DAY),    -- ★ 미래여야 결제 세션이 열린다
     DATE_ADD(CURDATE(), INTERVAL 33 DAY),
     10000, 'RECRUITING', 0);

-- ---------- 3. 확인 ----------
SELECT '측정 대상 챌린지 ID' AS label, challenge_id AS value
    FROM challenge WHERE name = 'payment-challenge'
UNION ALL
SELECT '참가비', entry_fee FROM challenge WHERE name = 'payment-challenge'
UNION ALL
SELECT '기존 참가자(0이어야 함)', COUNT(*) FROM challenge_user cu
    JOIN challenge c ON cu.challenge_id = c.challenge_id
    WHERE c.name = 'payment-challenge'
UNION ALL
SELECT '쓸 수 있는 게스트 수', COUNT(*) FROM user WHERE email LIKE 'guest%@oneul.store';
