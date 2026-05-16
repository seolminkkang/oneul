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
import store.oneul.mvc.payment.dto.CompensationResultDTO;
import store.oneul.mvc.payment.service.CompensationService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentUsecase {

    private final PaymentValidator paymentValidator;
    private final TossConfirmService tossConfirmService;
    private final PaymentSaveService paymentSaveService;
    private final CompensationService compensationService;


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
            log.error("결제 저장 실패 → 보상 트랜잭션 진입, orderId: {}", tossResponse.getOrderId(), e);

            // 6. 보상 흐름은 CompensationService가 담당
            CompensationResultDTO compensationResult = compensationService.compensate(event);


            // 7. Toss Cancel 성공
            if (compensationResult.isRefunded()) {
                return PaymentResultResponse.refunded(
                        tossResponse,
                        compensationResult.getRefundAmount(),
                        compensationResult.getRefundedAt()
                );
            }

            // 8. Toss Cancel 실패 → Redis Queue 적재 후 환불 대기 응답
            return PaymentResultResponse.refundPending(tossResponse);
        }

    }
}
