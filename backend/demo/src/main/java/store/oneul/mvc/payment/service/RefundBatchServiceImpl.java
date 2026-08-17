package store.oneul.mvc.payment.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.calculator.RefundAmountCalculator;
import store.oneul.mvc.payment.client.RefundTossGateway;
import store.oneul.mvc.payment.dao.PaymentDAO;
import store.oneul.mvc.payment.dao.RefundBatchDAO;
import store.oneul.mvc.payment.dto.RefundBatchResult;
import store.oneul.mvc.payment.dto.RefundReceiptDTO;
import store.oneul.mvc.payment.dto.RefundTargetDTO;
import store.oneul.mvc.payment.enums.RefundReason;
import store.oneul.mvc.payment.exception.AlreadyCanceledException;

/**
 * 챌린지 종료 환급 배치.
 *
 * ★ 측정용으로 두 가지를 실행 시점에 바꿀 수 있게 했다. 서버를 다시 띄우지 않고
 *   조건을 바꿔가며 재기 위해서다.
 *
 *   concurrency : 동시에 처리할 건수 (1 = 순차)
 *   aggregate   : challenge_finance 집계 갱신 방식
 *                   none   — 갱신하지 않음 (지금까지의 동작)
 *                   perRow — 건별로 UPDATE ... SET total = total + ?  ← 같은 행을 3,000번 친다
 *                   once   — 챌린지당 1회 집계
 *
 * 측정 결과는 notes/wiki/refund-batch-*.md 참고.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundBatchServiceImpl implements RefundBatchService {

    private final RefundBatchDAO refundBatchDAO;
    private final PaymentDAO paymentDAO;
    private final RefundTossGateway tossGateway;

    /** 재현용: 이 값의 배수인 paymentId 는 취소 후 기록에서 실패시킨다. 0 = 주입 안 함 */
    private volatile int failRecordEvery = 0;
    /** ALREADY_CANCELED_PAYMENT 를 성공으로 볼 것인가 (후보 ③의 Before/After 스위치) */
    private volatile boolean treatAlreadyCanceledAsSuccess = false;

    @Override
    public RefundBatchResult run() {
        return run(1, "none", 0, false);
    }

    @Override
    public RefundBatchResult run(int concurrency, String aggregate) {
        return run(concurrency, aggregate, 0, false);
    }

    @Override
    public RefundBatchResult run(int concurrency, String aggregate,
                                 int failRecordEvery, boolean treatAlreadyCanceledAsSuccess) {
        this.failRecordEvery = failRecordEvery;
        this.treatAlreadyCanceledAsSuccess = treatAlreadyCanceledAsSuccess;
        long startedAt = System.currentTimeMillis();
        tossGateway.resetCallCount();

        List<Long> challengeIds = refundBatchDAO.findEndedChallengeIds();
        AtomicInteger target = new AtomicInteger();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        ExecutorService pool = concurrency > 1 ? Executors.newFixedThreadPool(concurrency) : null;
        try {
            for (Long challengeId : challengeIds) {
                List<RefundTargetDTO> targets = refundBatchDAO.findRefundTargets(challengeId);
                target.addAndGet(targets.size());

                if (pool == null) {
                    for (RefundTargetDTO t : targets) {
                        count(refundOne(t, aggregate), success, fail);
                    }
                } else {
                    List<java.util.concurrent.Future<Boolean>> futures = targets.stream()
                            .map(t -> pool.submit(() -> refundOne(t, aggregate)))
                            .toList();
                    for (java.util.concurrent.Future<Boolean> f : futures) {
                        try {
                            count(f.get(), success, fail);
                        } catch (Exception e) {
                            fail.incrementAndGet();
                        }
                    }
                }

                if ("once".equals(aggregate)) {
                    refundBatchDAO.aggregateFinanceOnce(challengeId);
                }

                // 대상이 남아 있어도 ENDED 로 바꾼다.
                // 남은 건은 is_refunded=false 라 다음 배치가 다시 집는다
                refundBatchDAO.markChallengeEnded(challengeId);
            }
        } finally {
            if (pool != null) {
                pool.shutdown();
                try {
                    pool.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        log.info("환급 배치 완료 — 동시 {} / 집계 {} / 대상 {}건 / 성공 {} / 실패 {} / 토스 호출 {} / {}ms",
                 concurrency, aggregate, target.get(), success.get(), fail.get(),
                 tossGateway.callCount(), elapsed);

        return new RefundBatchResult(concurrency, aggregate, challengeIds.size(), target.get(),
                                     success.get(), fail.get(), tossGateway.callCount(), elapsed);
    }

    private void count(boolean ok, AtomicInteger success, AtomicInteger fail) {
        if (ok) success.incrementAndGet();
        else fail.incrementAndGet();
    }

    /** 1건 처리. 한 건이 실패해도 나머지는 계속 간다 */
    private boolean refundOne(RefundTargetDTO t, String aggregate) {
        int refundAmount = RefundAmountCalculator.calculate(
                t.getEntryFee(), t.getStartDate(), LocalDate.now(), RefundReason.CHALLENGE_SUCCESS);
        try {
            tossGateway.cancel(t.getPaymentKey(), refundAmount, "챌린지 성공 환급");

            // ★ 재현용 결함 주입 — 취소는 성공했는데 기록이 실패하는 상황.
            // 서버가 죽거나 DB가 순간 끊기면 실제로 벌어지는 지점이다.
            if (failRecordEvery > 0 && t.getPaymentId() % failRecordEvery == 0) {
                throw new IllegalStateException("기록 실패 주입 (paymentId=" + t.getPaymentId() + ")");
            }

            paymentDAO.insertRefundReceipt(buildReceipt(t, refundAmount));
            refundBatchDAO.markRefunded(t.getChallengeId(), t.getUserId(), refundAmount);

            if ("perRow".equals(aggregate)) {
                // ★ 같은 행 하나를 대상 건수만큼 갱신한다. 동시 실행에서 행 락 경합이 난다
                refundBatchDAO.addFinance(t.getChallengeId(), refundAmount);
            }
            return true;

        } catch (AlreadyCanceledException e) {
            // ★ 여기가 갈림길이다.
            // 토스가 "이미 취소됨"이라고 답한 것은 실패가 아니라 "지난번에 성공했다"는 뜻이다.
            // 이걸 실패로 처리하면 돈은 나갔는데 우리 DB는 영원히 미환급으로 남는다.
            if (!treatAlreadyCanceledAsSuccess) {
                log.warn("환급 실패로 처리 (paymentId: {}) - {}", t.getPaymentId(), e.getMessage());
                return false;
            }
            try {
                paymentDAO.insertRefundReceipt(buildReceipt(t, refundAmount));
                refundBatchDAO.markRefunded(t.getChallengeId(), t.getUserId(), refundAmount);
                log.info("이미 취소된 건을 기록으로 맞춤 (paymentId: {})", t.getPaymentId());
                return true;
            } catch (Exception ex) {
                log.warn("기록 복구 실패 (paymentId: {}) - {}", t.getPaymentId(), ex.toString());
                return false;
            }

        } catch (Exception e) {
            // 여기서 삼키는 것이 맞다. 남은 건은 다음 배치가 다시 집는다.
            log.warn("환급 실패 (paymentId: {}) - {}", t.getPaymentId(), e.toString());
            return false;
        }
    }

    private RefundReceiptDTO buildReceipt(RefundTargetDTO t, int refundAmount) {
        RefundReceiptDTO receipt = new RefundReceiptDTO();
        receipt.setPaymentId(t.getPaymentId());   // ★ 멱등키. 같은 결제건은 UNIQUE 에 막힌다
        receipt.setCancelFailLogId(null);
        receipt.setUserId(t.getUserId());
        receipt.setChallengeId(t.getChallengeId());
        receipt.setRefundMethod("TOSS_AUTO");
        receipt.setRefundAmount(refundAmount);
        receipt.setRefundedAt(LocalDateTime.now());
        receipt.setTransactionId("TOSS_CANCEL_" + t.getPaymentKey());
        receipt.setNote("orderId: " + t.getOrderId() + " / CHALLENGE_SUCCESS");
        return receipt;
    }
}
