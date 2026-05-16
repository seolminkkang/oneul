package store.oneul.mvc.payment.usecase;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.dto.PaymentConfirmRequest;
import store.oneul.mvc.payment.dto.PaymentResultResponse;
import store.oneul.mvc.payment.dto.PaymentSessionDto;
import store.oneul.mvc.payment.dto.TossConfirmResponse;
import store.oneul.mvc.payment.enums.RefundReason;
import store.oneul.mvc.payment.event.PaymentConfirmedEvent;
import store.oneul.mvc.payment.service.PaymentSaveService;
import store.oneul.mvc.payment.service.RefundReceiptService;
import store.oneul.mvc.payment.service.TossCancelService;
import store.oneul.mvc.payment.service.TossConfirmService;
import store.oneul.mvc.payment.validator.PaymentValidator;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentUsecase {

    private final PaymentValidator paymentValidator;
    private final TossConfirmService tossConfirmService;
    private final PaymentSaveService paymentSaveService;
    private final TossCancelService tossCancelService;
    private final RefundReceiptService refundReceiptService;

    public PaymentResultResponse confirmPayment(Long userId, PaymentConfirmRequest request) {

        // 1. Redis + DB 기반 검증
        PaymentSessionDto session = paymentValidator.validate(userId, request);

        // 2. Toss API 결제 승인
        TossConfirmResponse tossResponse = tossConfirmService.confirm(request);

        // 3. PaymentConfirmedEvent 생성 (재사용)
        PaymentConfirmedEvent event = new PaymentConfirmedEvent(
                userId,
                session.getChallengeId(),
                tossResponse.getOrderId(),
                tossResponse.getPaymentKey(),
                tossResponse.getAmount()
        );

        try {
            // 4. DB 저장 시도 (내부 @Transactional)
            paymentSaveService.save(userId, session.getChallengeId(), tossResponse);

            // 5. 성공 응답
            return PaymentResultResponse.success(tossResponse);

        } catch (Exception e) {
            log.error("결제 저장 실패 → TossCancel 보상 처리 진입", e);

            try {
                // 6. Toss 결제 취소
                int refundAmount = tossCancelService.cancel(event, RefundReason.TX_FAIL);

                // 7. 환불 내역 저장 (환불 시각은 지금 시간 기준)
                String refundedAt = java.time.LocalDateTime.now().toString(); // ISO 8601 포맷

                refundReceiptService.recordAutoRefund(event, refundAmount, "결제 저장 실패에 따른 자동 환불");

                // 8. 응답 반환
                return PaymentResultResponse.refunded(tossResponse, refundAmount, refundedAt);

            } catch (Exception ex) {
                log.error("TossCancel 또는 환불기록 실패 → 수동 환불 전환 필요", ex);

                // 9. 수동 환불 응답 반환
                return PaymentResultResponse.refundPending(tossResponse);
            }
        }
    }
}
