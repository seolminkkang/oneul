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

    /** ★ 측정용 스위치. 기본값 켜짐. 끄면 보상 없이 방치되는 Before 상태가 된다 */
    @org.springframework.beans.factory.annotation.Value("${payment.compensation-enabled:true}")
    private boolean compensationEnabled;


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

            // ★ 측정용: 보상 흐름을 끈 상태를 만든다 (Before 조건).
            // 끄면 토스에는 돈이 들어와 있는데 우리 DB 에는 아무 기록도 없는 상태로 남는다.
            // 그게 "미환불"이고, 그 건수가 이 항목의 Before 숫자다.
            // 기본값은 켜짐이다 — 설정을 빠뜨렸을 때 돈이 방치되는 쪽으로 실패해선 안 된다.
            if (!compensationEnabled) {
                log.warn("보상 흐름이 꺼져 있다 (측정 조건). orderId: {}", tossResponse.getOrderId());
                return PaymentResultResponse.refundPending(tossResponse);
            }

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
