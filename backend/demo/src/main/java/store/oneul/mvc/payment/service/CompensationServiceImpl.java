package store.oneul.mvc.payment.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.enums.RefundReason;
import store.oneul.mvc.payment.event.PaymentConfirmedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationServiceImpl implements CompensationService {

    private final TossCancelService tossCancelService;
    private final RefundReceiptService refundReceiptService;
    private final CancelDLQService cancelDLQService;
    
    @Override
    public void handleConfirmFail(PaymentConfirmedEvent event) {
        try {
            log.warn("보상 트랜잭션 시작 - Toss Cancel 요청");

            int refundAmount = tossCancelService.cancel(event, RefundReason.TX_FAIL);

            // db 저장 실패로 인한 환불
            refundReceiptService.recordAutoRefund(event, refundAmount, "DB 저장 실패로 인한 자동 환불");

        } catch (Exception e) {
            log.error("Toss Cancel 실패 - DLQ 적재 예정", e);
            // TODO: Redis DLQ 적재
            cancelDLQService.pushToQueue(event);
        }
    }
}
