package store.oneul.mvc.feed.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import store.oneul.mvc.feed.dto.ChallengeFeedDTO;
import store.oneul.mvc.feed.dto.CommunityFeedDTO;
import store.oneul.mvc.feed.dto.FeedDTO;
import store.oneul.mvc.feed.dto.FeedEvaluationRequest;
import store.oneul.mvc.feed.dto.StreakDTO;

@Mapper
public interface FeedDAO {
    public void createFeed(@Param("challengeId") Long challengeId, @Param("feedDTO") FeedDTO feedDTO);

    public void updateFeed(@Param("challengeId") Long challengeId, @Param("feedDTO") FeedDTO feedDTO);

    public void updateFeedContent(@Param("challengeId") Long challengeId, @Param("id") Long id, @Param("content") String content);

    public void deleteFeed(@Param("challengeId") Long challengeId, @Param("id") Long id);

    public ChallengeFeedDTO getFeed(@Param("challengeId") Long challengeId, @Param("id") Long id);

    public List<FeedDTO> getMyFeeds(@Param("userId") Long userId);

    public List<CommunityFeedDTO> getCommunityFeeds();

    public List<ChallengeFeedDTO> getChallengeFeeds(@Param("challengeId") Long challengeId,
                                                    @Param("limit") int limit,
                                                    @Param("offset") int offset);

    public List<StreakDTO> getStreak(@Param("userId") Long userId);

    public List<FeedDTO> getFeeds(@Param("challengeId") Long challengeId,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);
    
    public void evaluateFeed(FeedEvaluationRequest feedEvaluationRequest);
}
