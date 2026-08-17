package store.oneul.mvc.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import store.oneul.mvc.payment.dto.TossCancelRequest;
import store.oneul.mvc.payment.exception.AlreadyCanceledException;

/**
 * 환급 배치가 토스 결제 취소를 부르는 통로.
 *
 * ★ 왜 스텁이 필요한가
 * 부하 측정용 시드의 payment_key 는 실제 토스에 존재하지 않는다.
 * 실제로 부르면 전건 실패해서 "환급이 오래 걸린다"를 잴 수 없고,
 * 3,000번을 외부 서비스에 쏘는 것 자체가 해선 안 되는 일이다.
 *
 * ★ 왜 sleep 인가 (즉시 반환하면 측정이 무너진다)
 * 실제 HTTP 호출은 응답이 올 때까지 그 스레드를 붙잡고 논다(블로킹).
 * sleep 은 CPU를 안 쓰면서 스레드만 점유하므로 그 상태를 그대로 흉내낸다.
 * 지연값 38ms 는 지어낸 값이 아니라 실측 median 이다. notes/wiki/toss-latency.md
 *
 * ★ 기본값은 **실제 호출**이다 (refund.toss.stub=false).
 * 설정을 빠뜨렸을 때 "돈이 안 나갔는데 성공으로 기록되는" 쪽으로 실패하면 안 된다.
 * 부하 측정을 할 때만 refund.toss.stub=true 로 켠다.
 */
@Component
@RequiredArgsConstructor
public class RefundTossGateway {

    private final TossClient tossClient;

    @Value("${refund.toss.stub:false}")
    private boolean stub;

    @Value("${refund.toss.stub-latency-ms:38}")
    private long stubLatencyMs;

    /** 실제로 몇 번 불렸는지. 중복 호출(= 이중 환급 시도) 탐지에 쓴다 */
    private final java.util.concurrent.atomic.AtomicLong callCount =
            new java.util.concurrent.atomic.AtomicLong();

    public long callCount() {
        return callCount.get();
    }

    public void resetCallCount() {
        callCount.set(0);
    }

    /**
     * ★ 이미 취소한 결제를 기억한다. 토스 문서에 있는 동작을 그대로 흉내내기 위한 것이다.
     * 같은 결제를 다시 취소하면 ALREADY_CANCELED_PAYMENT(400)가 온다.
     * https://docs.tosspayments.com/reference/error-codes
     *
     * 스텁이 두 번째 호출도 성공시키면 "취소는 됐는데 기록이 실패한" 상황을 재현할 수 없다.
     */
    private final java.util.Set<String> canceledKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Value("${refund.toss.stub-strict:true}")
    private boolean stubStrict;

    public void resetCanceledKeys() {
        canceledKeys.clear();
    }

    public void cancel(String paymentKey, int refundAmount, String reason) {
        callCount.incrementAndGet();
        if (stub) {
            if (stubStrict && !canceledKeys.add(paymentKey)) {
                throw new AlreadyCanceledException(paymentKey);
            }
            try {
                Thread.sleep(stubLatencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("환급 배치 중단됨", e);
            }
            return;
        }
        tossClient.cancel(paymentKey, new TossCancelRequest(refundAmount, reason));
    }
}
