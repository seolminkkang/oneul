package store.oneul.mvc.feed.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import store.oneul.mvc.feed.service.FeedLikeService;
import store.oneul.mvc.user.dto.UserDTO;

/**
 * 피드 좋아요.
 *
 * 프론트(FeedDetailItem.tsx)에 하트 버튼은 있었지만 서버로 보내는 곳이 없어
 * 화면 상태만 바뀌고 새로고침하면 사라졌다. 그 연결을 만든다.
 */
@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedLikeController {

    private final FeedLikeService feedLikeService;

    /**
     * 좋아요 토글.
     *
     * 토글 후의 실제 개수(likes)를 함께 돌려준다. 클라이언트가 ±1 로 추측하면
     * 다른 기기에서 이미 눌러둔 경우 값이 어긋난다. 조회가 한 번 더 붙지만
     * (feed_like 의 UNIQUE(feed_id,user_id) 인덱스를 타는 COUNT) 값이 확정된다.
     */
    @PostMapping("/{feedId}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable Long feedId,
                                                          @AuthenticationPrincipal UserDTO user) {
        boolean liked = feedLikeService.toggleLike(feedId, user.getUserId());
        return ResponseEntity.ok(Map.of(
                "feedId", feedId,
                "liked", liked,
                "likes", feedLikeService.countLikes(feedId)));
    }

    /** 검증용 — feed_like 실제 행 수. like_count 와 어긋나는지 확인할 때 쓴다 */
    @GetMapping("/{feedId}/like/count")
    public ResponseEntity<Map<String, Object>> countLikes(@PathVariable Long feedId) {
        return ResponseEntity.ok(Map.of("feedId", feedId, "likes", feedLikeService.countLikes(feedId)));
    }
}
