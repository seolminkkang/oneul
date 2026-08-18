package store.oneul.mvc.payment.service;


import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.challenge.dao.ChallengeDAO;
import store.oneul.mvc.challenge.dto.ChallengeUserDTO;
import store.oneul.mvc.payment.dao.PaymentDAO;
import store.oneul.mvc.payment.dto.PaymentDTO;
import store.oneul.mvc.payment.dto.TossConfirmResponse;
import store.oneul.mvc.payment.event.PaymentConfirmedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSaveServiceImpl implements PaymentSaveService{

    private final PaymentDAO paymentDAO;
    private final ChallengeDAO challengeDAO;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * ★ 측정용 결함 주입. 0 = 주입 안 함 (기본값).
     *
     * "토스 결제는 성공했는데 내부 저장이 실패한" 상황은 실제로 서버가 죽거나 DB 가
     * 순간 끊길 때 생긴다. 그 상황을 재현할 방법이 없어서 스위치를 둔다.
     * 이력서에 써놓은 "발생할 수 있었습니다"를 **제약 제거 후 재현**으로 바꾸기 위한 것이다.
     *
     * 1 이면 전건 실패, 10 이면 10건마다 1건 실패.
     */
    @org.springframework.beans.factory.annotation.Value("${payment.fail-save-every:0}")
    private int failSaveEvery;

    private final java.util.concurrent.atomic.AtomicLong saveAttempt =
            new java.util.concurrent.atomic.AtomicLong();

    @Transactional
    public void save(Long userId, Long challengeId, TossConfirmResponse tossResponse) {
        // ★ 결함 주입 — 두 INSERT 보다 먼저 던진다.
        // 실제로 문제가 되는 지점은 "토스는 성공했는데 우리 쪽이 아무것도 못 남긴" 상태다.
        if (failSaveEvery > 0 && saveAttempt.incrementAndGet() % failSaveEvery == 0) {
            throw new IllegalStateException(
                    "내부 저장 실패 주입 (orderId=" + tossResponse.getOrderId() + ")");
        }

        // 1. payment INSERT
        PaymentDTO payment = new PaymentDTO();
        payment.setUserId(userId);
        payment.setChallengeId(challengeId);
        payment.setOrderId(tossResponse.getOrderId());
        payment.setPaymentKey(tossResponse.getPaymentKey());
        payment.setAmount(tossResponse.getAmount());
        payment.setStatus("PAID");
        int result = paymentDAO.insertPayment(payment);
        if (result != 1) {
        	System.out.println(result);
        	log.error("[결제 실패] payment insert 실패! orderId: {}", tossResponse.getOrderId());
        	throw new RuntimeException("payment insert failed");
        }
        // 2. challenge_user INSERT
        ChallengeUserDTO challengeUser = new ChallengeUserDTO();
        challengeUser.setUserId(userId);
        challengeUser.setChallengeId(challengeId);
        challengeUser.setSuccessDay(0);
        challengeUser.setRefunded(false);
        challengeUser.setRefundAmount(0);
        result = challengeDAO.insertChallengeUser(challengeUser);
        if (result != 1) {
        	
        	log.error("[결제 실패] challengeUser insert 실패! ");
            throw new RuntimeException("ChallengeUser insert failed");
        }
        // 3. 이벤트 발행 (실패 대비)
        eventPublisher.publishEvent(
            new PaymentConfirmedEvent(
                userId,
                challengeId,
                tossResponse.getOrderId(),
                tossResponse.getPaymentKey(),
                tossResponse.getAmount()
            )
        );
    }
}
