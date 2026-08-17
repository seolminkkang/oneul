package store.oneul.mvc.payment.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.dto.RefundBatchResult;
import store.oneul.mvc.payment.service.RefundBatchService;

/**
 * 챌린지 종료 환급 배치의 자동 실행 경로.
 *
 * 그동안 실행 경로가 측정용 수동 트리거뿐이었다(RefundBatchController).
 * 설계상으로는 주기적으로 도는 배치인데 그 경로가 없었다.
 *
 * ★ 주기를 1시간으로 잡은 이유
 * 종료 판정은 `end_date < CURDATE()` 라서 대상은 하루 단위로만 생긴다.
 * 그러면 하루 한 번으로 충분해 보이는데 1시간으로 잡았다.
 * 실패 건을 재시도 큐에 넣지 않고 **다음 배치가 다시 집게** 설계했기 때문이다
 * (notes/wiki/refund-batch-design.md Q5). 즉 배치 주기가 곧 복구 지연 시간이다.
 * 하루 주기면 실패 건이 최대 24시간 미환급으로 남는다. 1시간이면 최대 1시간이다.
 * 대상이 없으면 조회 한 번으로 끝나므로 빈 실행 비용은 무시할 수 있다.
 *
 * ★ 동시 처리 수 10의 근거 (2026-08-17 재측정으로 25에서 변경)
 * 3,000건 + 부하 초당 400건에서 상한을 바꿔가며 같은 세션에서 측정했다.
 *
 *   상한 없음 : 배치 4.93초, 조회 p95 976ms (평상시의 182배)  ← 배치도 더 느리다
 *   100       : 배치 4.62초, 조회 p95 137ms (25.5배)
 *   50        : 배치 4.75초, 조회 p95  49ms ( 9.2배)
 *   25        : 배치 6.38초, 조회 p95  14ms ( 2.7배)
 *   10        : 배치 14.7초, 조회 p95 6.4ms ( 1.18배)  ← 채택
 *
 * 배치 시간은 50에서 포화된다. 그 위로는 배치가 빨라지지 않으면서 조회만 나빠진다.
 * 10과 25 중 10을 골랐다 — 1시간 주기로 도는 백그라운드 작업에서 8초 차이는
 * 의미가 없고, 조회 p95 가 2.7배에서 1.18배로 떨어진다.
 * **포기한 것: 배치 시간 2.3배.**
 * 상세: notes/wiki/refund-batch-concurrency.md
 *
 * ★ fixedDelay 를 쓴 이유 (cron 이 아니라)
 * 이전 실행이 끝난 뒤부터 간격을 센다. 배치가 길어져도 다음 실행과 겹치지 않는다.
 *
 * ★ 서버가 여러 대면 중복 실행된다 — 분산 락을 넣지 않았다
 * 우리 규모는 단일 인스턴스다. 그리고 중복 실행되더라도
 * `refund_receipt.payment_id` UNIQUE 가 이중 환급을 막는다(멱등키).
 * 낭비되는 것은 외부 API 호출 횟수이지 돈이 아니다.
 * **재검토 조건: 서버를 2대 이상으로 늘리면 분산 락을 넣는다.**
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "refund.batch.scheduler-enabled", matchIfMissing = true)
public class RefundBatchScheduler {

    private final RefundBatchService refundBatchService;

    /** 측정으로 정한 값. 근거는 위 주석 */
    @Value("${refund.batch.concurrency:10}")
    private int concurrency;

    /**
     * initialDelay 를 두는 이유: 기동 직후에 돌면 배포·측정 준비 중에 끼어든다.
     */
    @Scheduled(
            fixedDelayString = "${refund.batch.interval-ms:3600000}",
            initialDelayString = "${refund.batch.initial-delay-ms:60000}")
    public void runRefundBatch() {
        try {
            RefundBatchResult result = refundBatchService.run(concurrency, "none");

            if (result.getTargetCount() == 0) {
                log.debug("환급 배치 — 대상 없음");
                return;
            }
            log.info("환급 배치 완료 — 대상 {}건 / 성공 {} / 실패 {} / {}ms",
                     result.getTargetCount(), result.getSuccessCount(),
                     result.getFailCount(), result.getElapsedMs());

            if (result.getFailCount() > 0) {
                // 실패 건은 is_refunded=false 로 남아 다음 배치가 다시 집는다.
                // 큐에 넣지 않으므로 여기서 할 일은 알리는 것뿐이다.
                log.warn("환급 배치 실패 {}건 — 다음 배치가 재시도한다", result.getFailCount());
            }
        } catch (Exception e) {
            // 스케줄러 안에서 예외가 새어나가면 다음 실행이 멈춘다.
            log.error("환급 배치 실행 중 예외", e);
        }
    }
}
