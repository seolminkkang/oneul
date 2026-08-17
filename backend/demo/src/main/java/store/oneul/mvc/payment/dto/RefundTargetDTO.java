package store.oneul.mvc.payment.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * 챌린지 종료 환급 배치가 처리할 대상 1건.
 *
 * 1건 = 결제 1건(payment.id). 참가자 기준이 아니라 결제 기준인 이유는
 * 멱등키가 refund_receipt.payment_id UNIQUE 이기 때문이다.
 * (notes/wiki/refund-batch-design.md Q4)
 *
 * 토스 취소 호출과 refund_receipt 기록에 필요한 값만 담는다.
 * 배치가 건당 추가 조회를 하지 않도록 한 번에 다 가져온다.
 */
@Data
public class RefundTargetDTO {
    private Long paymentId;       // 멱등키. refund_receipt.payment_id 로 들어간다
    private String orderId;
    private String paymentKey;    // 토스 취소 호출에 필요
    private Long userId;
    private Long challengeId;
    private int amount;           // 실제 결제 금액

    private int entryFee;         // 환급액 계산 입력 (RefundAmountCalculator)
    private LocalDate startDate;  // 환급액 계산 입력 (CHALLENGE_SUCCESS 에선 안 쓰지만 시그니처가 요구)
}
