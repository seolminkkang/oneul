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

    @Transactional
    public void save(Long userId, Long challengeId, TossConfirmResponse tossResponse) {
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
