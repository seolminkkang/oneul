package store.oneul.mvc.security.jwt;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import store.oneul.mvc.security.rtr.RefreshTokenService;

@Component
@RequiredArgsConstructor
public class JwtReissueFilter extends OncePerRequestFilter {

    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String accessToken = extractToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        String refreshToken = request.getHeader("Refresh");

        if (accessToken != null && refreshToken != null) {
            try {
                jwtProvider.getUserIdFromToken(accessToken); // 정상 동작
            } catch (Exception ex) {
                System.out.println("[FILTER] AccessToken expired or invalid. Attempting reissue.");
                Optional<String> newAccessToken = refreshTokenService.reissue(refreshToken, accessToken);
                newAccessToken.ifPresent(token -> {
                    response.setHeader("Authorization", "Bearer " + token);
                    System.out.println("[FILTER] New AccessToken set in response header.");
                });
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(String bearerHeader) {
        if (bearerHeader != null && bearerHeader.startsWith("Bearer ")) {
            return bearerHeader.substring(7);
        }
        return null;
    }
}
