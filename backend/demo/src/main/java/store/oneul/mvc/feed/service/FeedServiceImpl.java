package store.oneul.mvc.feed.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import store.oneul.mvc.feed.dao.FeedDAO;
import store.oneul.mvc.feed.dto.ChallengeFeedDTO;
import store.oneul.mvc.feed.dto.CommunityFeedDTO;
import store.oneul.mvc.feed.dto.FeedDTO;
import store.oneul.mvc.feed.dto.FeedEvaluationRequest;
import store.oneul.mvc.feed.dto.StreakDTO;
import store.oneul.mvc.feed.enums.CheckStatus;
import store.oneul.mvc.upload.util.S3PresignedUrlGenerator;
import store.oneul.mvc.workoutLog.dao.WorkoutLogDAO;
import store.oneul.mvc.workoutLog.dto.WorkoutLogInsertRequestDTO;

@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {
    private final FeedDAO feedDAO;
    private final WorkoutLogDAO workoutLogDAO;
    private final S3PresignedUrlGenerator s3PresignedUrlGenerator;

    @Override
    public void createFeed(Long challengeId, FeedDTO feedDTO) {
        feedDAO.createFeed(challengeId, feedDTO);
    }

    @Override
    public void updateFeed(Long challengeId, FeedDTO feedDTO) {
        feedDAO.updateFeed(challengeId, feedDTO);
    }

    @Override
    public void updateFeedContent(Long challengeId, Long id, String content) {
        feedDAO.updateFeedContent(challengeId, id, content);
    }

    @Override
    public void deleteFeed(Long challengeId, Long id) {
        feedDAO.deleteFeed(challengeId, id);
    }

    @Override
    public ChallengeFeedDTO getFeed(Long challengeId, Long id) {
        ChallengeFeedDTO feed = feedDAO.getFeed(challengeId, id);
        if (!feed.getImageUrl().startsWith("uploads")) {
            return feed;
        }
        String presignedUrl = s3PresignedUrlGenerator.getPresignedUrlToDownload(feed.getImageUrl());
        feed.setImageUrl(presignedUrl);
        return feed;
    }

    /** 상한을 넘기거나 음수를 주면 안전한 값으로 되돌린다 */
    private int clampLimit(int limit) {
        if (limit <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private int clampOffset(int offset) {
        return Math.max(offset, 0);
    }

    @Override
    public List<FeedDTO> getFeeds(Long challengeId, int limit, int offset) {
        List<FeedDTO> feeds = feedDAO.getFeeds(challengeId, clampLimit(limit), clampOffset(offset));
        for (FeedDTO feed : feeds) {
            if (!feed.getImageUrl().startsWith("uploads")) {
                continue;
            }
            String presignedUrl = s3PresignedUrlGenerator.getPresignedUrlToDownload(feed.getImageUrl());
            feed.setImageUrl(presignedUrl);
        }
        return feeds;
    }

    @Override
    public List<FeedDTO> getMyFeeds(Long userId) {
        List<FeedDTO> myFeeds = feedDAO.getMyFeeds(userId);
        for (FeedDTO feed : myFeeds) {
            if (!feed.getImageUrl().startsWith("uploads")) {
                continue;
            }
            String presignedUrl = s3PresignedUrlGenerator.getPresignedUrlToDownload(feed.getImageUrl());
            feed.setImageUrl(presignedUrl);
        }
        return myFeeds;
    }

    @Override
    public List<CommunityFeedDTO> getCommunityFeeds() {
        List<CommunityFeedDTO> communityFeeds = feedDAO.getCommunityFeeds();
        for (CommunityFeedDTO communityFeed : communityFeeds) {
            if (!communityFeed.getImageUrl().startsWith("uploads")) {
                continue;
            }
            String presignedUrl = s3PresignedUrlGenerator.getPresignedUrlToDownload(communityFeed.getImageUrl());
            communityFeed.setImageUrl(presignedUrl);
        }
        return communityFeeds;
        
    }

    @Override
    public List<ChallengeFeedDTO> getChallengeFeeds(Long challengeId, int limit, int offset) {
        List<ChallengeFeedDTO> challengeFeeds =
                feedDAO.getChallengeFeeds(challengeId, clampLimit(limit), clampOffset(offset));
        for (ChallengeFeedDTO challengeFeed : challengeFeeds) {
            if (!challengeFeed.getImageUrl().startsWith("uploads")) {
                continue;
            }
            String presignedUrl = s3PresignedUrlGenerator.getPresignedUrlToDownload(challengeFeed.getImageUrl());
            challengeFeed.setImageUrl(presignedUrl);
        }
        return challengeFeeds;
    }

    @Override
    public List<StreakDTO> getStreak(Long userId) {
        return feedDAO.getStreak(userId);
    }

    @Transactional
    @Override
    public void evaluateFeed(FeedEvaluationRequest req, Long userId) {
        feedDAO.evaluateFeed(req);
		if(CheckStatus.APPROVED == req.getCheckStatus()) {
			WorkoutLogInsertRequestDTO workoutLogReq = WorkoutLogInsertRequestDTO.builder()
	                .challengeId(req.getChallengeId())
	                .feedId(req.getId())
	                .userId(userId)
	                .build();
			workoutLogDAO.insertWorkoutLog(workoutLogReq);
		}
	}

}
