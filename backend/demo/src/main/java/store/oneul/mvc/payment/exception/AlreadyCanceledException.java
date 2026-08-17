package store.oneul.mvc.payment.exception;

/**
 * 토스가 "이미 취소된 결제"라고 응답한 상황.
 *
 * ALREADY_CANCELED_PAYMENT (400) — "이미 취소된 결제 입니다."
 * https://docs.tosspayments.com/reference/error-codes
 *
 * ★ 이건 실패가 아니라 "이미 성공"이라는 신호다.
 * 실패로 처리하면 그 건은 영원히 미환급으로 남는다 — 돈은 이미 나갔는데도.
 */
public class AlreadyCanceledException extends RuntimeException {

    public AlreadyCanceledException(String paymentKey) {
        super("ALREADY_CANCELED_PAYMENT: " + paymentKey);
    }
}
