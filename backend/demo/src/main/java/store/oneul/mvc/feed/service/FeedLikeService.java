package store.oneul.mvc.feed.service;

public interface FeedLikeService {

    /** 좋아요 토글. 반환값은 토글 후 상태 (true = 눌린 상태) */
    boolean toggleLike(Long feedId, Long userId);

    /** 검증용 — feed_like 실제 행 수 */
    int countLikes(Long feedId);
}
