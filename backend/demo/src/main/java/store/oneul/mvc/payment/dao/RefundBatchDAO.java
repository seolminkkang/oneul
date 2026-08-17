package store.oneul.mvc.payment.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import store.oneul.mvc.payment.dto.RefundTargetDTO;

/**
 * 챌린지 종료 환급 배치 전용 조회.
 *
 * 설계 근거는 notes/wiki/refund-batch-design.md.
 * - 종료 판정은 end_date 로 고르고, 배치가 challenge_status 를 ENDED 로 바꾼다 (Q2)
 * - 이미 환급된 건은 challenge_user.is_refunded 로 거른다 (Q1·Q3, 성능용 필터)
 * - 이중 환급의 최종 방어선은 refund_receipt.payment_id UNIQUE 다 (Q4)
 *
 * ★ 인덱스는 일부러 넣지 않았다. Before 측정이 최적화된 상태에서 시작하면
 *   개선 폭을 잴 수 없다.
 */
@Mapper
public interface RefundBatchDAO {

    /** 어제까지 끝났는데 아직 ENDED 로 안 바뀐 챌린지 id */
    List<Long> findEndedChallengeIds();

    /** 해당 챌린지의 환급 대상 (달성 + 미환급 + 결제 살아있음) */
    List<RefundTargetDTO> findRefundTargets(Long challengeId);

    /** 대상 건수만. 배치를 돌리지 않고 규모를 확인할 때 쓴다 */
    int countRefundTargets(Long challengeId);

    /**
     * 환급 완료 표시. 다음 배치가 이 건을 다시 집지 않게 하는 필터다.
     * 이게 어긋나도 이중 환급은 refund_receipt.payment_id UNIQUE 가 막는다 (Q1·Q4)
     */
    int markRefunded(@Param("challengeId") Long challengeId,
                     @Param("userId") Long userId,
                     @Param("refundAmount") int refundAmount);

    /** 종료 처리. 상태는 조건이 아니라 배치가 남기는 결과다 (Q2) */
    int markChallengeEnded(Long challengeId);

    /**
     * ★ 집계를 건별로 누적한다. 챌린지 1개당 행이 1개뿐이라
     * 1,000건이 같은 행을 순서대로 갱신하게 된다 (hot row 경합)
     */
    int addFinance(@Param("challengeId") Long challengeId,
                   @Param("refundAmount") int refundAmount);

    /** 집계를 챌린지당 1회로 끝낸다. 위와 결과는 같고 경합만 사라진다 */
    int aggregateFinanceOnce(Long challengeId);
}
