package store.oneul.mvc.payment.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.dto.PaymentConfirmRequest;
import store.oneul.mvc.payment.dto.TossConfirmResponse;

/**
 * 결제 승인·취소 스텁. ★ 계측 장비다. 기능이 아니다.
 *
 * ★ 왜 필요한가
 * 보상 트랜잭션을 재려면 "결제는 성공했는데 내부 저장이 실패한" 상황을 N건 만들어야 한다.
 * 그런데 실제 토스 결제 승인은 카드 정보와 결제창이 필요해서 부하로 만들 수 없다.
 * (환급 배치에서 RefundTossGateway 를 만든 것과 같은 이유)
 *
 * ★ 왜 "미환불 건수"를 여기서 세는가
 * 보상 흐름이 없으면 우리 DB 에는 아무 기록도 남지 않는다(트랜잭션 롤백).
 * 그런데 **토스에는 돈이 들어와 있다.** 그 상태가 "미환불"이고, 우리 DB 로는 셀 수 없다.
 * 그래서 스텁이 승인된 결제 집합을 들고 있다가 취소될 때 뺀다.
 *   미환불 = 승인됐는데 취소되지 않은 건수
 *
 * ★ 지연 38ms 는 실측값이다 (notes/wiki/toss-latency.md). 즉시 반환하면 동시성이 재현되지 않는다.
 *
 * ★ 기본값은 꺼짐이다. payment.toss.stub=true 일 때만 빈이 만들어진다.
 *   설정을 빠뜨렸을 때 "돈이 안 움직였는데 성공으로 기록되는" 쪽으로 실패해선 안 된다.
 */
@Slf4j
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "payment.toss.stub", havingValue = "true")
public class TossStub {

    @Value("${payment.toss.stub-latency-ms:38}")
    private long latencyMs;

    /** 승인됐고 아직 취소되지 않은 결제. 이 집합의 크기가 곧 "미환불 건수"다 */
    private final Set<String> outstanding = ConcurrentHashMap.newKeySet();

    private final AtomicLong confirmCount = new AtomicLong();
    private final AtomicLong cancelCount = new AtomicLong();
    private final AtomicLong alreadyCanceledCount = new AtomicLong();

    public TossConfirmResponse confirm(PaymentConfirmRequest request) {
        sleep();
        confirmCount.incrementAndGet();
        outstanding.add(request.getPaymentKey());

        TossConfirmResponse res = new TossConfirmResponse();
        res.setOrderId(request.getOrderId());
        res.setPaymentKey(request.getPaymentKey());
        res.setAmount(request.getAmount());
        res.setStatus("DONE");
        return res;
    }

    /**
     * 취소. 이미 취소된 결제를 다시 취소하면 예외를 던진다 — 토스 문서에 있는 동작이다.
     * (ALREADY_CANCELED_PAYMENT). 이걸 성공시키면 "취소는 됐는데 기록이 실패한" 상황을
     * 재현할 수 없다.
     */
    public void cancel(String paymentKey) {
        sleep();
        cancelCount.incrementAndGet();
        if (!outstanding.remove(paymentKey)) {
            alreadyCanceledCount.incrementAndGet();
            throw new RuntimeException("ALREADY_CANCELED_PAYMENT: " + paymentKey);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("스텁 지연 중 인터럽트", e);
        }
    }

    /** ★ 측정의 주 지표: 승인됐는데 취소되지 않은 건수 */
    public int outstandingCount() {
        return outstanding.size();
    }

    public long confirmCount() {
        return confirmCount.get();
    }

    public long cancelCount() {
        return cancelCount.get();
    }

    /** 취소를 두 번 시도한 횟수. 보상이 중복 실행되는지 탐지한다 */
    public long alreadyCanceledCount() {
        return alreadyCanceledCount.get();
    }

    public void reset() {
        outstanding.clear();
        confirmCount.set(0);
        cancelCount.set(0);
        alreadyCanceledCount.set(0);
    }
}
