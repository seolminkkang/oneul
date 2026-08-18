package store.oneul.mvc.payment.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.service.CompensationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationEventHandler {

    private final CompensationService compensationService;

    /** ★ 측정용 스위치. PaymentUsecase 와 같은 값을 쓴다 */
    @org.springframework.beans.factory.annotation.Value("${payment.compensation-enabled:true}")
    private boolean compensationEnabled;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onRollback(PaymentConfirmedEvent event) {
        if (!compensationEnabled) {
            log.warn("보상 흐름이 꺼져 있다 (측정 조건). orderId: {}", event.getOrderId());
            return;
        }
        log.warn("트랜잭션 롤백 감지 - Toss 결제 취소 시도");
        compensationService.compensate(event);
    }
}
