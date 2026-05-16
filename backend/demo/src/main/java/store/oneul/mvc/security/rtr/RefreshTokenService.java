package store.oneul.mvc.security.rtr;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import store.oneul.mvc.security.jwt.JwtProvider;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenDao refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final Duration REFRESH_TOKEN_EXPIRATION = Duration.ofDays(14);

    public String createAndSaveRefreshToken(Long userId) {
        String refreshToken = UUID.randomUUID().toString();
        //refreshTokenRepository.save(String.valueOf(userId), refreshToken, REFRESH_TOKEN_EXPIRATION);
        return refreshToken;
    }

    public boolean validate(String userId, String providedToken) {
        String savedToken = refreshTokenRepository.findByUserId(userId);
        return savedToken != null && savedToken.equals(providedToken);
    }

    public void delete(String userId) {
        refreshTokenRepository.delete(userId);
    }

    public Optional<String> reissue(String refreshToken, String accessToken) {
        try {
            Long userId = jwtProvider.getUserIdFromToken(accessToken);
            String userIdStr = String.valueOf(userId);

            String saved = refreshTokenRepository.findByUserId(userIdStr);
            if (!refreshToken.equals(saved)) {
                System.out.println("[RTR] RefreshToken mismatch for userId = " + userIdStr);
                return Optional.empty();
            }

            String newAccessToken = jwtProvider.createToken(userId);

            refreshTokenRepository.delete(userIdStr);
            String newRefreshToken = UUID.randomUUID().toString();
           // refreshTokenRepository.save(userIdStr, newRefreshToken, REFRESH_TOKEN_EXPIRATION);

            System.out.println("[RTR] Access & Refresh Token reissued for userId = " + userIdStr);

            return Optional.of(newAccessToken);
        } catch (Exception e) {
            System.out.println("[RTR] Error during token reissue: " + e.getMessage());
            return Optional.empty();
        }
    }
}
