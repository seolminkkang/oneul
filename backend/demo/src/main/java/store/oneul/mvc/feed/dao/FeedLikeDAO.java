package store.oneul.mvc.feed.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 피드 좋아요.
 *
 * ★ 원래 상태 (2026-08-17 확인)
 * - `feed_like` 테이블은 있었다 (UNIQUE(feed_id, user_id) 포함)
 * - 프론트에 하트 버튼도 있었다 (FeedDetailItem.tsx)
 * - **그런데 서버 API가 없어서 화면 상태만 바뀌고 새로고침하면 사라졌다.** feed_like 는 0행
 * - 그 결과 `feed.like_count` 가 한 번도 안 변해서,
 *   커뮤니티 피드의 `ORDER BY like_count DESC, created_at DESC` 가 사실상 최신순이었다
 *
 * ★ 일부러 가장 단순하게 만든다
 * `feed_like` 에 기록하고 `feed.like_count` 를 +1 하는 방식이다.
 * 인기 피드일수록 같은 행 하나를 여럿이 동시에 갱신하게 되므로(hot row) 경합이 난다.
 * 처음부터 잘 만들면 Before 가 사라져 개선 폭을 잴 수 없다.
 */
@Mapper
public interface FeedLikeDAO {

    /** 이 사용자가 이미 눌렀는가 */
    int existsLike(@Param("feedId") Long feedId, @Param("userId") Long userId);

    /** 좋아요 기록. UNIQUE(feed_id, user_id) 가 중복을 막는다 */
    int insertLike(@Param("feedId") Long feedId, @Param("userId") Long userId);

    /** 좋아요 취소 */
    int deleteLike(@Param("feedId") Long feedId, @Param("userId") Long userId);

    /** ★ 비정규화 카운터 갱신. 같은 행을 동시에 치면 여기서 락이 걸린다 */
    int increaseLikeCount(@Param("feedId") Long feedId);

    int decreaseLikeCount(@Param("feedId") Long feedId);

    /** 검증용 — 기록의 실제 개수. like_count 와 어긋나는지 확인할 때 쓴다 */
    int countLikes(@Param("feedId") Long feedId);
}
