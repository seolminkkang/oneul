package store.oneul.mvc.security.jwt;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import store.oneul.mvc.security.rtr.RefreshTokenService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class JwtReissueController {

    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;

    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(@RequestHeader("Authorization") String accessTokenHeader,
                                     @RequestHeader("Refresh") String refreshToken) {
        String accessToken = accessTokenHeader.replace("Bearer ", "");

        return refreshTokenService.reissue(refreshToken, accessToken)
            .map(newAccessToken -> ResponseEntity.ok(Map.of("accessToken", newAccessToken)))
            .orElseGet(() -> {
                System.out.println("[REISSUE] Failed to reissue token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                     .body(Map.of("error", "토큰 재발급 실패"));
            });
    }

    @DeleteMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String accessTokenHeader) {
        String accessToken = accessTokenHeader.replace("Bearer ", "");

        try {
            Long userId = jwtProvider.getUserIdFromToken(accessToken);
            refreshTokenService.delete(String.valueOf(userId));

            System.out.println("[LOGOUT] Successfully logged out userId = " + userId);
            return ResponseEntity.ok("로그아웃 완료");
        } catch (Exception e) {
            System.out.println("[LOGOUT] Invalid token during logout: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("잘못된 토큰");
        }
    }
}
