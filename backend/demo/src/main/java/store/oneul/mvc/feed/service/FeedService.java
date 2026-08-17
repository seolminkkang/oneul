package store.oneul.mvc.feed.service;

import java.util.List;

import store.oneul.mvc.feed.dto.CommunityFeedDTO;
import store.oneul.mvc.feed.dto.FeedDTO;
import store.oneul.mvc.feed.dto.FeedEvaluationRequest;
import store.oneul.mvc.feed.dto.StreakDTO;
import store.oneul.mvc.feed.dto.ChallengeFeedDTO;

public interface FeedService {

    /** 한 번에 내려줄 수 있는 최대 건수. 파라미터로 전체 조회를 되살리지 못하게 막는다 */
    int MAX_PAGE_SIZE = 100;

    /** 요청하지 않았을 때의 기본 건수 */
    int DEFAULT_PAGE_SIZE = 20;

    public void createFeed(Long challengeId, FeedDTO feedDTO);

    public void updateFeed(Long challengeId, FeedDTO feedDTO);

    public void updateFeedContent(Long challengeId, Long id, String content);

    public void deleteFeed(Long challengeId, Long id);

    public ChallengeFeedDTO getFeed(Long challengeId, Long id);

    public List<FeedDTO> getFeeds(Long challengeId, int limit, int offset);

    public List<FeedDTO> getMyFeeds(Long userId);

    public List<CommunityFeedDTO> getCommunityFeeds();

    public List<ChallengeFeedDTO> getChallengeFeeds(Long challengeId, int limit, int offset);

    public List<StreakDTO> getStreak(Long userId);

    public void evaluateFeed(FeedEvaluationRequest feedEvaluationRequest, Long userId);
}
