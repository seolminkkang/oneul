package store.oneul.mvc.feed.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import store.oneul.mvc.feed.dto.ContentDTO;
import store.oneul.mvc.feed.dto.FeedDTO;
import store.oneul.mvc.feed.dto.FeedEvaluationRequest;
import store.oneul.mvc.feed.dto.ChallengeFeedDTO;
import store.oneul.mvc.feed.service.FeedService;
import store.oneul.mvc.user.dto.UserDTO;

@RestController
@RequestMapping("api/challenges/{challengeId}/feeds")
@RequiredArgsConstructor
public class FeedController {
    private final FeedService feedService;

    @GetMapping
    public ResponseEntity<List<FeedDTO>> getFeeds(
            @PathVariable Long challengeId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(feedService.getFeeds(challengeId, limit, offset));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChallengeFeedDTO> getFeed(@PathVariable Long challengeId, @PathVariable Long id) {
        return ResponseEntity.ok(feedService.getFeed(challengeId, id));
    }

    @GetMapping("/challenge")
    public ResponseEntity<List<ChallengeFeedDTO>> getChallengeFeeds(
            @PathVariable Long challengeId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(feedService.getChallengeFeeds(challengeId, limit, offset));
    }

    @PostMapping
    public ResponseEntity<Void> createFeed(
            @PathVariable Long challengeId,
            @RequestBody FeedDTO feedDTO,
            @AuthenticationPrincipal UserDTO user
    ) {
        feedDTO.setChallengeId(challengeId);
        feedDTO.setUserId(user.getUserId());
        feedService.createFeed(challengeId, feedDTO);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FeedDTO> updateFeed(@PathVariable Long challengeId, @PathVariable Long id, @RequestBody FeedDTO feedDTO) {
        feedDTO.setId(id);
        feedService.updateFeed(challengeId, feedDTO);
        return ResponseEntity.ok(feedDTO);
    }

    @PatchMapping("/{id}/content")
    public ResponseEntity<Void> updateFeedContent(@PathVariable Long challengeId, @PathVariable Long id, @RequestBody ContentDTO contentDTO) {
        feedService.updateFeedContent(challengeId, id, contentDTO.getContent());
        return ResponseEntity.ok().build();
    }
    
    @PatchMapping("/{id}/checks")
    public ResponseEntity<ChallengeFeedDTO> evaluateFeed(
    		@PathVariable Long challengeId, 
    		@PathVariable Long id, 
    		@RequestBody FeedEvaluationRequest feedEvaluationRequest,
    		@AuthenticationPrincipal UserDTO user
    ) {
    	feedEvaluationRequest.setChallengeId(challengeId);
    	feedEvaluationRequest.setId(id);
    	Long userId = user.getUserId();
    	feedService.evaluateFeed(feedEvaluationRequest, userId);
    	ChallengeFeedDTO feedDTO = feedService.getFeed(challengeId, feedEvaluationRequest.getId());
    	return ResponseEntity.ok(feedDTO);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteFeed(@PathVariable Long challengeId, @RequestBody FeedDTO feedDTO) {
        feedService.deleteFeed(challengeId, feedDTO.getId());
        return ResponseEntity.noContent().build();
    }

}
