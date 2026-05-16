package store.oneul.mvc.security.oauth;

import java.io.IOException;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import store.oneul.mvc.security.oauth.service.OAuthService;
import store.oneul.mvc.security.token.TokenHelper;
import store.oneul.mvc.user.dto.LoginResultDTO;
import store.oneul.mvc.user.dto.UserDTO;
import store.oneul.mvc.user.service.UserService;


// 실제 ID Token 받아서 처리하는 진짜 로그인 후처리 담당
@Component
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "frontend")
@Setter
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final GoogleIdTokenVerifier tokenVerifier;
    private final OAuthService oauthService;
    private final UserService userService;
    private final TokenHelper tokenHelper;
    private String url;
    private String redirectPath;
    private String accessTokenCookieName;
    private String refreshTokenCookieName;
    private int accessTokenExpirationTime;
    private int refreshTokenExpirationTime;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        if (!(oAuth2User instanceof OidcUser oidcUser)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not an OIDC user");
            return;
        }

        if (oidcUser.getIdToken() == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "ID Token is missing");
            return;
        }

        String idToken = oidcUser.getIdToken().getTokenValue();

        try {
            Map<String, Object> claims = tokenVerifier.verify(idToken);

            String email = (String) claims.get("email");
            String name = (String) claims.get("name");
            String picture = (String) claims.get("picture");

            UserDTO user = userService.findByEmail(email);
            if (user == null) {
                user = UserDTO.builder()
                        .email(email)
                        .username(name)
                        .profileImg(picture)
                        .oauthProvider("google")
                        .build();
                userService.insertUser(user);
                user = userService.findByEmail(email);
            }
            LoginResultDTO loginResult = oauthService.login(user);
           
            boolean signupCompleted = Boolean.TRUE.equals(user.getSignupCompleted());

            Cookie accessTokenCookie = tokenHelper.createAccessTokenCookie(loginResult.getAccessToken());
            Cookie refreshTokenCookie = tokenHelper.createRefreshTokenCookie(loginResult.getRefreshToken());

            response.addCookie(accessTokenCookie);
            response.addCookie(refreshTokenCookie);

            // signupCompleted만 URL 파라미터로 전달
            String redirectUrl = UriComponentsBuilder
                    .fromUriString(url + redirectPath)
                    .queryParam("signupCompleted", signupCompleted)
                    .build()
                    .toUriString();

            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid ID Token");
        }
    }
}
