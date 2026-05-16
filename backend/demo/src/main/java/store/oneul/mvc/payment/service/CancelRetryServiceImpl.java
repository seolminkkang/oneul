package store.oneul.mvc.payment.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.dao.PaymentDAO;
import store.oneul.mvc.payment.dto.CancelFailLogDTO;
import store.oneul.mvc.payment.dto.CancelRetryPayload;
import store.oneul.mvc.payment.enums.RefundReason;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelRetryServiceImpl implements CancelRetryService {

    private static final String DLQ_KEY = "payment:cancel:queue";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private final TossCancelService tossCancelService;
    private final RefundReceiptService refundReceiptService;
    private final PaymentDAO paymentDAO;
    

    @Override
    public void retryOnce() {
        String json = redisTemplate.opsForList().leftPop(DLQ_KEY);
        if (json == null) return;

        try {
            CancelRetryPayload payload = objectMapper.readValue(json, CancelRetryPayload.class);
            log.warn("DLQ 재시도 시작 - orderId: {}, retry: {}", payload.getOrderId(), payload.getRetry());

            // TossCancel 재시도
            int refundAmount = tossCancelService.cancel(payload, RefundReason.valueOf(payload.getRefundType()));

            // 환불 내역 저장
            refundReceiptService.recordRetryRefund(payload, refundAmount, "DLQ 재시도 환불 성공");

            log.info("DLQ 재시도 성공 - orderId: {}", payload.getOrderId());

        } catch (Exception e) {
            log.error("DLQ 재시도 실패", e);

            try {
                CancelRetryPayload failed = objectMapper.readValue(json, CancelRetryPayload.class);
                int nextRetry = failed.getRetry() + 1;

                if (nextRetry >= 3) {
                    log.error("DLQ 3회 실패 - 수동 환불 전환 필요 (orderId: {})", failed.getOrderId());

                    // 1. paymentId 조회
                    Long paymentId = null;
                    try {
                        paymentId = paymentDAO.findIdByOrderId(failed.getOrderId());
                    } catch (Exception ex) {
                        log.warn("paymentId 조회 실패 - orderId: {}, 예외: {}", failed.getOrderId(), ex.getMessage());
                    }

                    // 2. RefundReason Enum 변환
                    RefundReason reason;
                    try {
                        reason = RefundReason.valueOf(failed.getRefundType());
                    } catch (IllegalArgumentException ie) {
                        log.warn("RefundReason 변환 실패: {}, 기본값 TX_FAIL 사용", failed.getRefundType());
                        reason = RefundReason.TX_FAIL;
                    }

                    // 3. CancelFailLogDTO 생성
                    CancelFailLogDTO logDTO = CancelFailLogDTO.builder()
                    	    .orderId(failed.getOrderId())
                    	    .paymentKey(failed.getPaymentKey())
                    	    .userId(failed.getUserId())
                    	    .challengeId(failed.getChallengeId())
                    	    .amount(failed.getAmount())
                    	    .type(reason.toDbValue())  // 여기에 적용
                    	    .errorStatus("DLQ_RETRY_3_FAILED")
                    	    .reason("DLQ 재시도 3회 실패")
                    	    .manualStatus("WAITING_FORM")
                    	    .build();


                    paymentDAO.insertCancelFailLog(logDTO);
                }
                else {
                    failed.setRetry(nextRetry);
                    String retryJson = objectMapper.writeValueAsString(failed);
                    redisTemplate.opsForList().rightPush(DLQ_KEY, retryJson);
                    log.warn("DLQ 재적재 완료 - orderId: {}, retry: {}", failed.getOrderId(), nextRetry);
                }

            } catch (Exception retryEx) {
                log.error("DLQ 재적재 실패 - orderId: {}", json, retryEx);
            }
        }
    }
}
