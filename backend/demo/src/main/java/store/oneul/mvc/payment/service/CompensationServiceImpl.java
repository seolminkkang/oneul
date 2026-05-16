package store.oneul.mvc.payment.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.dto.CompensationResultDTO;
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
    public CompensationResultDTO compensate(PaymentConfirmedEvent event) {
        try {
            log.warn("보상 트랜잭션 시작 - Toss Cancel 요청, orderId: {}", event.getOrderId());

            int refundAmount = tossCancelService.cancel(event, RefundReason.TX_FAIL);

            String refundedAt = LocalDateTime.now().toString();

            refundReceiptService.recordAutoRefund(
                    event,
                    refundAmount,
                    "DB 저장 실패로 인한 자동 환불"
            );

            log.info("보상 트랜잭션 성공 - 자동 환불 완료, orderId: {}", event.getOrderId());

            return CompensationResultDTO.refunded(refundAmount, refundedAt);

        } catch (Exception e) {
            log.error("Toss Cancel 또는 환불 기록 실패 - Redis Queue 적재, orderId: {}", event.getOrderId(), e);

            cancelDLQService.pushToQueue(event);

            return CompensationResultDTO.pending();
        }
    }
}