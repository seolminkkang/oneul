package store.oneul.mvc.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 환급 배치 1회 실행 결과. 측정에 쓰는 숫자가 전부 여기 담긴다.
 */
@Data
@AllArgsConstructor
public class RefundBatchResult {
    private int concurrency;     // 이번 실행의 동시 실행 수
    private String aggregate;    // 집계 갱신 방식 (none / perRow / once)
    private int challengeCount;  // 종료 판정된 챌린지 수
    private int targetCount;     // 환급 대상 건수
    private int successCount;
    private int failCount;
    private long tossCallCount;  // 토스 취소를 실제로 몇 번 불렀나 (중복 호출 탐지용)
    private long elapsedMs;      // ★ Before/After 의 주 지표
}
