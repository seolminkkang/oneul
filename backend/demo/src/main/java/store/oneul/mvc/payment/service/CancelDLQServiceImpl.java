package store.oneul.mvc.payment.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.payment.dto.CancelRetryPayload;
import store.oneul.mvc.payment.enums.RefundReason;
import store.oneul.mvc.payment.event.PaymentConfirmedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelDLQServiceImpl implements CancelDLQService {

    private static final String DLQ_KEY = "payment:cancel:queue";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void pushToQueue(PaymentConfirmedEvent event) {
        try {
            CancelRetryPayload payload = CancelRetryPayload.of(event, RefundReason.TX_FAIL, 0);

            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForList().rightPush(DLQ_KEY, json);
            log.warn("DLQ 적재 완료 - orderId: {}, retry: {}", payload.getOrderId(), payload.getRetry());

        } catch (Exception e) {
            log.error("DLQ Redis 적재 실패 - orderId: {}", event.getOrderId(), e);
        }
    }
}
