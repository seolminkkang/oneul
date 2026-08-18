package store.oneul.mvc.upload.util;

import java.net.URL;
import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import store.oneul.mvc.common.exception.InvalidParameterException;
import store.oneul.mvc.upload.config.AwsProperties;
import store.oneul.mvc.upload.dto.PresignedUrlResponse;
import store.oneul.mvc.upload.exception.ImageUploadException;

@Component
@Data
@RequiredArgsConstructor
public class S3PresignedUrlGenerator {
    private final AwsProperties awsProperties;
    private final Duration signatureDuration = Duration.ofMinutes(5);
    private final S3Presigner presigner;

    public PresignedUrlResponse generatePresignedUrl(String filename, String contentType) {
        validateFilename(filename);
        String uuid = UUID.randomUUID().toString();
        String extension = filename.substring(filename.lastIndexOf('.'));
        String objectKey = "uploads/" + uuid + extension;
        try{
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(awsProperties.getBucketName())
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(signatureDuration)
                    .putObjectRequest(putObjectRequest)
                    .build();

            URL presignedUrl = presigner.presignPutObject(presignRequest).url();
           return new PresignedUrlResponse(presignedUrl.toString(), objectKey);
        } catch (Exception e) {
            throw new ImageUploadException("이미지 업로드 실패: " + e.getMessage(), e);
        }
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new InvalidParameterException("파일명이 없습니다.");
        }
    }

    /** 다운로드 서명의 유효기간. 아래 캐시 TTL 보다 반드시 길어야 한다 */
    private static final Duration DOWNLOAD_SIGNATURE_DURATION = Duration.ofMinutes(10);

    /**
     * ★ 캐시 TTL 은 서명 유효기간의 절반이다.
     *
     * 캐시된 URL 은 만들어진 시점부터 늙는다. TTL 을 유효기간과 같게 두면
     * 캐시 만료 직전에 받은 사용자는 잔여 수명이 0에 가까운 URL 을 받는다.
     * 절반으로 두면 **최악의 경우에도 잔여 5분을 보장**한다.
     *
     * 이게 이 캐시에서 포기한 것이다 — 서명을 10분 쓸 수 있는데 5분만 재사용한다.
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * ★ 왜 캐시하는가
     * presignGetObject 는 네트워크 호출이 아니라 로컬 SigV4 HMAC 연산이고,
     * 입력이 (버킷, 키, 유효기간)뿐이라 **같은 키는 항상 같은 결과**다.
     * 그런데 조회 건마다 매번 다시 만들고 있었다.
     *
     * 측정: 커뮤니티 피드(12건) 응답 중 서명 생성이 약 20~31ms — 응답의 절반 이상.
     * 피드 목록(20건)에서는 약 40ms 로, 깊은 페이지 개선(19.6→2.95ms)의 효과를 덮었다.
     * 근거: notes/wiki/community-feed-sort-index.md, feed-list-paging.md
     *
     * 크기 상한 10,000: 피드 45,000개를 전부 담지 않는다. 조회되는 것은 최근·인기 쪽에
     * 몰리므로 상한을 두고 LRU 로 밀어낸다. URL 하나가 약 200바이트라 최대 약 2MB.
     */
    private final com.github.benmanes.caffeine.cache.Cache<String, String> urlCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(CACHE_TTL)
                    .build();

    public String getPresignedUrlToDownload(String filename) {
        validateFilename(filename);
        // 캐시 미스일 때만 서명을 만든다. 같은 키면 같은 URL 이므로 재사용해도 안전하다
        return urlCache.get(filename, this::generatePresignedDownloadUrl);
    }

    private String generatePresignedDownloadUrl(String filename) {
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(awsProperties.getBucketName())
                    .key(filename)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(DOWNLOAD_SIGNATURE_DURATION)
                    .getObjectRequest(objectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);

            return presignedRequest.url().toExternalForm();
        } catch (Exception e) {
            throw new ImageUploadException("이미지 다운로드 실패: " + e.getMessage(), e);
        }
    }
}
