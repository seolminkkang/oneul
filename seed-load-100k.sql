-- ===============================
-- 부하 측정용 시드 — 10만 건 확장판 (2026-08-12)
--
-- 재현하려는 상황:
--   "배치가 어제 끝난 챌린지 3개(=3,000건)를 환급하는 동안,
--    사용자들은 진행중인 챌린지에서 인증하고 조회한다"
--
--   끝난 챌린지 3개 × 달성자 1,000명 = 3,000건   <- 배치가 처리할 것
--   진행중 챌린지 1개 × 참가자 1,000명           <- 부하 도구가 때릴 것
--
-- 규모 근거: notes/wiki/refund-batch-design.md "목표 규모"
--
-- 부하 유저 이메일을 guest{N}@oneul.store 로 맞춘 이유:
--   OAuthServiceImpl.java:44 의 guest-login 이 이 형식만 찾는다.
--   k6가 토큰을 받으려면 이 형식이어야 한다. (guest101 ~ guest1100)
--   기존 guest1, guest2 와 겹치지 않게 101부터 시작한다.
--
-- 사용법 (몇 번을 돌려도 같은 상태가 된다)
--   docker exec -i oneul-mysql mysql -uroot -p1234 --default-character-set=utf8mb4 < seed-load.sql
-- ===============================

USE oneul;

SET SESSION cte_max_recursion_depth = 10000;

-- ---------- 초기화 (재실행 가능하게) ----------
-- 부하용 데이터의 유일한 식별자는 user.oauth_provider = 'LOAD' 와
-- challenge.name LIKE 'load-challenge-%' 두 개다. 이메일로 거르지 않는다
-- (guest101~guest1100 이라 'guest1%' 로는 guest200 이 안 걸린다 — 실제로 겪은 버그).
--
-- 외래키 때문에 자식 테이블부터 지운다. 순서를 바꾸면 실패한다.

-- 임시 테이블은 쓰지 않는다. MySQL 은 한 쿼리에서 임시 테이블을 두 번 참조하면
-- "Can't reopen table" 로 실패한다 (실제로 겪음). JOIN 으로 지운다.

DELETE r FROM refund_receipt r
    LEFT JOIN `user` u     ON r.user_id = u.user_id
    LEFT JOIN challenge c  ON r.challenge_id = c.challenge_id
    WHERE u.oauth_provider = 'LOAD' OR c.name LIKE 'load-challenge-%';
DELETE l FROM cancel_fail_log l
    LEFT JOIN `user` u     ON l.user_id = u.user_id
    LEFT JOIN challenge c  ON l.challenge_id = c.challenge_id
    WHERE u.oauth_provider = 'LOAD' OR c.name LIKE 'load-challenge-%';
DELETE p FROM payment p
    LEFT JOIN `user` u     ON p.user_id = u.user_id
    LEFT JOIN challenge c  ON p.challenge_id = c.challenge_id
    WHERE u.oauth_provider = 'LOAD' OR c.name LIKE 'load-challenge-%';
-- 부하 테스트가 만든 인증 기록·피드도 지운다 (안 지우면 실행할수록 테이블이 커진다)
DELETE w FROM workout_log w
    LEFT JOIN `user` u     ON w.user_id = u.user_id
    LEFT JOIN challenge c  ON w.challenge_id = c.challenge_id
    WHERE u.oauth_provider = 'LOAD' OR c.name LIKE 'load-challenge-%';
DELETE fl FROM feed_like fl
    LEFT JOIN `user` u     ON fl.user_id = u.user_id
    LEFT JOIN feed f       ON fl.feed_id = f.id
    LEFT JOIN challenge c  ON f.challenge_id = c.challenge_id
    WHERE u.oauth_provider = 'LOAD' OR c.name LIKE 'load-challenge-%';
DELETE f FROM feed f
    LEFT JOIN `user` u     ON f.user_id = u.user_id
    LEFT JOIN challenge c  ON f.challenge_id = c.challenge_id
    WHERE u.oauth_provider = 'LOAD' OR c.name LIKE 'load-challenge-%';
DELETE cu FROM challenge_user cu
    LEFT JOIN `user` u     ON cu.user_id = u.user_id
    LEFT JOIN challenge c  ON cu.challenge_id = c.challenge_id
    WHERE u.oauth_provider = 'LOAD' OR c.name LIKE 'load-challenge-%';
DELETE ch FROM challenge_chat ch
    LEFT JOIN `user` u     ON ch.user_id = u.user_id
    LEFT JOIN challenge c  ON ch.challenge_id = c.challenge_id
    WHERE u.oauth_provider = 'LOAD' OR c.name LIKE 'load-challenge-%';
DELETE cf FROM challenge_finance cf
    JOIN challenge c ON cf.challenge_id = c.challenge_id
    WHERE c.name LIKE 'load-challenge-%';
DELETE fo FROM follow fo
    LEFT JOIN `user` u1 ON fo.follower_id  = u1.user_id
    LEFT JOIN `user` u2 ON fo.following_id = u2.user_id
    WHERE u1.oauth_provider = 'LOAD' OR u2.oauth_provider = 'LOAD';
DELETE s FROM streak s
    JOIN `user` u ON s.user_id = u.user_id
    WHERE u.oauth_provider = 'LOAD';
DELETE FROM challenge WHERE name LIKE 'load-challenge-%';
DELETE FROM `user` WHERE oauth_provider = 'LOAD';

-- ---------- 1. 부하용 사용자 1,000명 (guest101 ~ guest1100) ----------
INSERT INTO `user` (username, email, oauth_provider, user_tel, nickname, user_del_fl, authority, signup_completed)
WITH RECURSIVE n AS (
    SELECT 1 AS i
    UNION ALL
    SELECT i + 1 FROM n WHERE i < 1000
)
SELECT
    CONCAT('load', i),
    CONCAT('guest', 100 + i, '@oneul.store'),   -- guest-login 이 찾는 형식
    'LOAD',
    CONCAT('010-1000-', LPAD(i, 4, '0')),
    CONCAT('부하유저', i),
    TRUE, FALSE, TRUE
FROM n;

-- ---------- 2-A. 어제 끝난 챌린지 100개 (배치가 집을 대상) ----------
-- ★ 목표 재선언: 1,000명 챌린지 100개가 같은 날 종료 = 10만 건
--   3,000건 판(seed-load.sql)과 구조는 같고 챌린지 수만 3 -> 100 이다.
-- end_date < CURDATE() 이고 아직 ENDED 가 아닌 것이 대상이다 (Q2 결정)
INSERT INTO challenge
    (name, owner_id, category_id, description, total_day, goal_day,
     is_challenge, is_public, start_date, end_date, entry_fee, challenge_status, member_count)
SELECT
    CONCAT('load-challenge-ended-', i),
    1, 1, '부하 측정용 종료 챌린지',
    30, 20, TRUE, TRUE,
    DATE_SUB(CURDATE(), INTERVAL 31 DAY),
    DATE_SUB(CURDATE(), INTERVAL 1 DAY),    -- 어제 종료
    10000,
    'IN_PROGRESS',                           -- 아직 ENDED 아님 = 배치가 집어야 할 상태
    1000
FROM (
    WITH RECURSIVE c AS (
        SELECT 1 AS i UNION ALL SELECT i + 1 FROM c WHERE i < 100
    )
    SELECT i FROM c
) t;

-- ---------- 2-B. 진행중 챌린지 1개 (부하 도구가 때릴 대상) ----------
INSERT INTO challenge
    (name, owner_id, category_id, description, total_day, goal_day,
     is_challenge, is_public, start_date, end_date, entry_fee, challenge_status, member_count)
VALUES
    ('load-challenge-live', 1, 1, '부하 측정용 진행중 챌린지',
     30, 20, TRUE, TRUE,
     DATE_SUB(CURDATE(), INTERVAL 10 DAY),
     DATE_ADD(CURDATE(), INTERVAL 20 DAY),   -- 아직 안 끝남 = 배치가 안 건드림
     10000, 'IN_PROGRESS', 1000);

INSERT INTO challenge_finance (challenge_id, success_headcount, total_refund)
SELECT challenge_id, 0, 0 FROM challenge WHERE name LIKE 'load-challenge-%';

-- ---------- 3. 참가자 ----------
-- 끝난 챌린지 3개: success_day(25) >= goal_day(20) 이므로 전원 달성자 = 전원 환급 대상
INSERT INTO challenge_user (challenge_id, user_id, success_day, is_refunded, refund_amount)
SELECT c.challenge_id, u.user_id, 25, FALSE, 0
FROM challenge c
JOIN `user` u ON u.username LIKE 'load%' AND u.oauth_provider = 'LOAD'
WHERE c.name LIKE 'load-challenge-ended-%';

-- 진행중 챌린지: 아직 진행중이라 달성 전
INSERT INTO challenge_user (challenge_id, user_id, success_day, is_refunded, refund_amount)
SELECT c.challenge_id, u.user_id, 8, FALSE, 0
FROM challenge c
JOIN `user` u ON u.username LIKE 'load%' AND u.oauth_provider = 'LOAD'
WHERE c.name = 'load-challenge-live';

-- ---------- 4. 참가비 결제 (끝난 챌린지 3,000건) ----------
-- 환급하려면 취소할 결제건이 있어야 한다. payment.id 가 멱등키 기준이다 (Q4 결정)
INSERT INTO payment
    (user_id, challenge_id, order_id, payment_key, amount, method, status, approved_at)
SELECT
    cu.user_id, cu.challenge_id,
    CONCAT('load_order_', cu.challenge_id, '_', cu.user_id),
    CONCAT('load_pk_',    cu.challenge_id, '_', cu.user_id),
    10000, 'CARD', 'PAID',
    DATE_SUB(NOW(), INTERVAL 31 DAY)
FROM challenge_user cu
JOIN challenge c ON c.challenge_id = cu.challenge_id
WHERE c.name LIKE 'load-challenge-ended-%';

-- ---------- 확인 ----------
SELECT '끝난 챌린지(100 목표)' AS 항목, COUNT(*) AS 값 FROM challenge WHERE name LIKE 'load-challenge-ended-%'
UNION ALL
SELECT '★ 환급 대상 건수', COUNT(*) FROM challenge_user cu
    JOIN challenge c ON c.challenge_id = cu.challenge_id
    WHERE c.name LIKE 'load-challenge-ended-%'
      AND cu.success_day >= c.goal_day AND cu.is_refunded = FALSE
UNION ALL
SELECT '결제건',            COUNT(*) FROM payment p
    JOIN challenge c ON c.challenge_id = p.challenge_id
    WHERE c.name LIKE 'load-challenge-ended-%'
UNION ALL
SELECT '진행중 챌린지 ID',  challenge_id FROM challenge WHERE name = 'load-challenge-live'
UNION ALL
SELECT '진행중 참가자',     COUNT(*) FROM challenge_user cu
    JOIN challenge c ON c.challenge_id = cu.challenge_id
    WHERE c.name = 'load-challenge-live';
