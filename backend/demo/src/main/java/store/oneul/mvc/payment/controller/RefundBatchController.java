package store.oneul.mvc.payment.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.dto.RefundBatchResult;
import store.oneul.mvc.payment.service.RefundBatchService;

/**
 * 환급 배치 수동 실행 — ★ 측정 전용 트리거.
 *
 * 설계상으로는 하루 한 번 스케줄러가 도는 것이지만 **아직 스케줄러를 붙이지 않았다.**
 * 지금은 이 엔드포인트가 유일한 실행 경로이고, 그 엔드포인트도 기본적으로 꺼져 있다
 * (아래 @ConditionalOnProperty). 즉 **운영에서는 실행되지 않는다.**
 * 이 엔드포인트는 부하를 주는 중에 원하는 시점에 배치를 켜기 위한 계측 장비다.
 *
 * ★ 동기 실행인 것은 실수가 아니다.
 * 요청을 받은 Tomcat 스레드가 3,000건을 다 돌 때까지 붙잡혀 있다.
 * "환급과 웹 요청이 같은 스레드 풀을 쓴다"는 상태가 여기서 완성되고,
 * 5단계에서 걷어낼 대상이 정확히 이것이다.
 *
 * ★ /api/** 가 아니라 /internal/** 인 이유
 * SecurityConfig 가 /api/** 를 인증 대상으로 잡고 있는데, 측정 스크립트는 토큰 없이 부른다.
 * 대신 refund.batch.manual-trigger=true 일 때만 빈이 만들어진다.
 * 설정을 켜지 않으면 이 엔드포인트는 아예 존재하지 않는다.
 */
@Slf4j
@RestController
@RequestMapping("/internal/refund-batch")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "refund.batch.manual-trigger", havingValue = "true")
public class RefundBatchController {

    private final RefundBatchService refundBatchService;
    private final store.oneul.mvc.payment.client.RefundTossGateway tossGateway;

    /**
     * @param concurrency 동시 실행 수 (1 = 순차)
     * @param aggregate   challenge_finance 갱신 방식: none / perRow / once
     */
    @PostMapping("/run")
    public ResponseEntity<RefundBatchResult> run(
            @RequestParam(defaultValue = "1") int concurrency,
            @RequestParam(defaultValue = "none") String aggregate,
            @RequestParam(defaultValue = "0") int failRecordEvery,
            @RequestParam(defaultValue = "false") boolean treatAlreadyCanceledAsSuccess,
            @RequestParam(defaultValue = "false") boolean resetStub) {
        if (resetStub) {
            tossGateway.resetCanceledKeys();
        }
        log.info("환급 배치 수동 실행 — 동시 {} / 집계 {} / thread: {}",
                 concurrency, aggregate, Thread.currentThread().getName());
        return ResponseEntity.ok(refundBatchService.run(concurrency, aggregate,
                failRecordEvery, treatAlreadyCanceledAsSuccess));
    }
}
