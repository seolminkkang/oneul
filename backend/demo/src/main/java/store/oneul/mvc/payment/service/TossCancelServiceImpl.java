package store.oneul.mvc.payment.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.oneul.mvc.challenge.dao.ChallengeDAO;
import store.oneul.mvc.challenge.dto.ChallengeDTO;
import store.oneul.mvc.payment.calculator.RefundAmountCalculator;
import store.oneul.mvc.payment.client.TossClient;
import store.oneul.mvc.payment.dto.CancelRetryPayload;
import store.oneul.mvc.payment.dto.TossCancelRequest;
import store.oneul.mvc.payment.enums.RefundReason;
import store.oneul.mvc.payment.event.PaymentConfirmedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class TossCancelServiceImpl implements TossCancelService {

    private final TossClient tossClient;
    private final ChallengeDAO challengeDAO;

    public int cancel(PaymentConfirmedEvent event, RefundReason reason) {
        ChallengeDTO challenge = challengeDAO.getChallengeById(event.getChallengeId());

        int refundAmount = RefundAmountCalculator.calculate(
            challenge.getEntryFee(),
            challenge.getStartDate(),
            LocalDate.now(),
            reason
        );

        TossCancelRequest request = new TossCancelRequest(refundAmount, "자동 환불 처리: " + reason.name());
        tossClient.cancel(event.getPaymentKey(), request);

        log.info("Toss 결제 취소 성공 (paymentKey: {}, refundAmount: {})", event.getPaymentKey(), refundAmount);
        return refundAmount;
    }
    
    @Override
    public int cancel(CancelRetryPayload payload, RefundReason reason) {
        ChallengeDTO challenge = challengeDAO.getChallengeById(payload.getChallengeId());

        int refundAmount = RefundAmountCalculator.calculate(
            challenge.getEntryFee(),
            challenge.getStartDate(),
            LocalDate.now(),
            reason
        );

        TossCancelRequest request = new TossCancelRequest(refundAmount, "자동 환불 처리: " + reason.name());
        tossClient.cancel(payload.getPaymentKey(), request);

        log.info("Toss 결제 취소 성공 (paymentKey: {}, refundAmount: {})", payload.getPaymentKey(), refundAmount);
        return refundAmount;
    }

}

