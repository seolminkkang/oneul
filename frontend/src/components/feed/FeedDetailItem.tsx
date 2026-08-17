import { toggleFeedLike } from "@/api/feed";
import { Feed } from "@/types/Feed";
import { formatTimeAgo } from "@/utils/date";
import { useState } from "react";
import { FaHeart } from "react-icons/fa";

function FeedDetailItem({
  feed,
  onClose,
}: {
  feed: Feed;
  onClose: () => void;
}) {
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(feed.likeCount);
  const [pending, setPending] = useState(false);

  /**
   * 좋아요 토글.
   *
   * 원래는 화면 상태만 바꿨다 — 서버로 보내는 곳이 없어서 새로고침하면 사라졌다.
   *
   * pending 으로 연타를 막는다. 같은 사용자의 요청이 겹치면
   * UNIQUE(feed_id, user_id) 를 확인하고 넣는 사이에 다른 요청이 끼어
   * 카운터가 실제 기록과 어긋난다 (부하 측정에서 확인).
   *
   * 낙관적으로 먼저 반영하고, 서버가 알려준 값으로 교정한다.
   * 실패하면 눌렀던 것을 되돌린다 — 화면만 눌린 상태로 남으면 안 된다.
   */
  const handleLike = async () => {
    if (pending) return;

    const prevLiked = liked;
    const prevCount = likeCount;

    setPending(true);
    setLiked(!prevLiked);
    setLikeCount(prevLiked ? prevCount - 1 : prevCount + 1);

    try {
      const res = await toggleFeedLike(feed.id);
      setLiked(res.liked);
      setLikeCount(res.likes);
    } catch {
      setLiked(prevLiked);
      setLikeCount(prevCount);
    } finally {
      setPending(false);
    }
  };

  return (
    <>
      {/* 상단: 닉네임 + 닫기 버튼 */}
      <div className="flex items-center justify-between border border-white/10 px-5 py-4">
        <div className="flex items-center gap-2">
          <img
            src={feed.profileImg || "/svgs/default-profile.svg"}
            alt="프로필"
            className="h-8 w-8 rounded-full object-cover"
          />
          <span className="text-sm font-semibold">{feed.nickname}</span>
        </div>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onClose();
          }}
          className="text-lg text-gray-400 hover:text-white"
        >
          ✕
        </button>
      </div>

      {/* 이미지 */}
      {feed.imageUrl && (
        <img
          src={feed.imageUrl}
          alt="Feed"
          className="h-[400px] w-full object-cover"
        />
      )}

      {/* 본문 */}
      <div className="space-y-4 border border-white/10 px-5 py-4 shadow backdrop-blur-md">
        <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-white">
          {feed.content}
        </p>

        {/* 시간 + 좋아요 */}
        <div className="flex items-center justify-between text-sm text-gray-400">
          <span>{formatTimeAgo(feed.createdAt)}</span>
          <button
            onClick={handleLike}
            disabled={pending}
            className="flex items-center gap-1 font-semibold hover:opacity-80 disabled:opacity-50"
          >
            <FaHeart
              size={14}
              className={liked ? "text-purple-400" : "text-gray-400"}
            />
            <span className={liked ? "text-purple-400" : "text-gray-400"}>
              {likeCount}
            </span>
          </button>
        </div>
      </div>
    </>
  );
}

export default FeedDetailItem;
