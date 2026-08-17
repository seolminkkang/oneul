package store.oneul.mvc.payment.service;

import store.oneul.mvc.payment.dto.RefundBatchResult;

public interface RefundBatchService {

    /** 종료된 챌린지의 달성자에게 참가비를 환급한다. 실행 1회 = 결과 1개 */
    RefundBatchResult run();

    /**
     * 측정용. 조건을 바꿔가며 재기 위해 실행 시점에 지정한다.
     * @param concurrency 동시에 처리할 건수 (1 = 순차)
     * @param aggregate   challenge_finance 갱신 방식: none / perRow / once
     */
    RefundBatchResult run(int concurrency, String aggregate);

    /**
     * 후보 ③ 재현용.
     * @param failRecordEvery 이 값의 배수인 paymentId 는 취소 성공 후 기록에서 실패시킨다 (0 = 없음)
     * @param treatAlreadyCanceledAsSuccess ALREADY_CANCELED_PAYMENT 를 성공으로 볼지
     */
    RefundBatchResult run(int concurrency, String aggregate,
                          int failRecordEvery, boolean treatAlreadyCanceledAsSuccess);
}
