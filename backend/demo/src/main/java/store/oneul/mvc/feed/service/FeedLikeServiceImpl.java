package store.oneul.mvc.feed.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import store.oneul.mvc.feed.dao.FeedLikeDAO;

/**
 * 좋아요 v1 — 가장 단순한 방식.
 *
 * 기록(feed_like)과 카운터(feed.like_count)를 한 트랜잭션으로 함께 바꾼다.
 *
 * ★ 이 방식이 갖는 문제 (측정 대상)
 * 1. **hot row** — 한 피드에 좋아요가 몰리면 `UPDATE feed SET like_count = like_count + 1`이
 *    같은 행 하나를 두고 줄을 선다. 인기 피드일수록 심하다
 * 2. **인덱스 항목 이동** — like_count 는 idx_feed_status_like_created 의 정렬 컬럼이라
 *    값이 바뀔 때마다 인덱스에서 자리를 옮긴다
 * 3. **확인 후 삽입(check-then-act)** — existsLike 로 보고 insertLike 하는데,
 *    그 사이에 다른 요청이 끼면 UNIQUE(feed_id, user_id) 에 걸린다.
 *    트랜잭션이 롤백되므로 카운터가 두 번 오르지는 않지만 **요청은 실패한다**
 *
 * 처음부터 잘 만들면 Before 가 사라지므로 일부러 이대로 두고 측정한다.
 */
@Service
@RequiredArgsConstructor
public class FeedLikeServiceImpl implements FeedLikeService {

    private final FeedLikeDAO feedLikeDAO;

    /**
     * ★ 2026-08-17 — 카운터 UPDATE 를 먼저 한다 (순서 뒤집기)
     *
     * 원래는 insert/delete 를 먼저 하고 카운터를 갱신했다. 그러면
     *   1) feed_like INSERT 가 FK 때문에 부모 feed 행에 **공유 락(S)** 을 건다
     *   2) 이어지는 UPDATE feed 가 같은 행에 **배타 락(X)** 을 요구한다
     * 두 트랜잭션이 1)에서 S 락을 함께 쥔 뒤 2)로 승격을 시도하면 서로 물려 데드락이 난다.
     * 한 피드에 초당 300건에서 **50.7% 가 500 으로 실패**했다. ([[like-deadlock]])
     *
     * UPDATE 를 먼저 하면 모두가 **같은 순서로 X 락을 잡는다.** 먼저 잡은 쪽이 끝날 때까지
     * 뒤에 온 쪽은 **락을 쥐지 않은 채** 기다리므로 물릴 일이 없다.
     * → 데드락이 **대기**로 바뀐다. 실패가 느려짐으로 바뀌는 것이므로 남는 장사다.
     *
     * 포기한 것: 피드당 처리량이 직렬화된다.
     * 안 쓴 것: 카운터 샤딩 · Redis 원자 연산 — 피드당 초당 2건 규모에 과하다
     *          (임계는 피드당 100건/초, 약 50배 여유). FK 제거는 무결성 책임이 앱으로 넘어간다.
     */
    @Transactional
    @Override
    public boolean toggleLike(Long feedId, Long userId) {
        if (feedLikeDAO.existsLike(feedId, userId) > 0) {
            feedLikeDAO.decreaseLikeCount(feedId);   // X 락을 먼저 잡는다
            feedLikeDAO.deleteLike(feedId, userId);
            return false;
        }
        feedLikeDAO.increaseLikeCount(feedId);       // X 락을 먼저 잡는다
        feedLikeDAO.insertLike(feedId, userId);
        return true;
    }

    @Override
    public int countLikes(Long feedId) {
        return feedLikeDAO.countLikes(feedId);
    }
}
