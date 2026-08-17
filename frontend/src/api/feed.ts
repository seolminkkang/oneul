import { post } from "./api";

export interface FeedLikeResult {
  feedId: number;
  liked: boolean;
  likes: number;
}

/**
 * 좋아요 토글.
 *
 * 서버가 토글 후의 상태(liked)와 실제 개수(likes)를 함께 돌려준다.
 * 클라이언트가 ±1 로 추측하지 않고 이 값을 그대로 쓴다.
 */
export const toggleFeedLike = async (
  feedId: number,
): Promise<FeedLikeResult> => {
  return await post<FeedLikeResult>(`/feeds/${feedId}/like`);
};
