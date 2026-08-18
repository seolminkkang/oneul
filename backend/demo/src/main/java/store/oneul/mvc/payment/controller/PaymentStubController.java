package store.oneul.mvc.payment.controller;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import store.oneul.mvc.payment.client.TossStub;

/**
 * 결제 스텁 상태 조회·초기화. ★ 계측 장비다.
 *
 * 보상 트랜잭션의 Before/After 를 재려면 "토스에는 돈이 들어와 있는데 우리 DB 에는
 * 기록이 없는" 건수를 알아야 하는데, 그건 우리 DB 로는 셀 수 없다(롤백됐으므로).
 * 스텁이 세는 값을 여기서 읽는다.
 *
 * payment.toss.stub=true 일 때만 존재한다 (TossStub 빈이 있어야 하므로).
 * /api/** 가 아니라 /internal/** 인 이유는 RefundBatchController 와 같다 —
 * 측정 스크립트가 토큰 없이 부른다.
 */
@RestController
@RequestMapping("/internal/payment-stub")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.toss.stub", havingValue = "true")
public class PaymentStubController {

    private final TossStub stub;

    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> state() {
        return ResponseEntity.ok(Map.of(
                // ★ 주 지표: 승인됐는데 취소되지 않은 건수 = 미환불
                "outstanding", stub.outstandingCount(),
                "confirmed", stub.confirmCount(),
                "canceled", stub.cancelCount(),
                // 취소를 두 번 시도한 횟수. 보상이 중복 실행되면 여기가 올라간다
                "alreadyCanceled", stub.alreadyCanceledCount()));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        stub.reset();
        return ResponseEntity.ok(Map.of("result", "reset"));
    }
}
