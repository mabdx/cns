package com.example.cns.security;

import com.example.cns.entities.AuthProvider;
import com.example.cns.entities.User;
import com.example.cns.repositories.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Value("${FRONTEND_URL:http://localhost:3000}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        log.info("OAuth2 Success Handler: Processing user {}", email);

        if (email != null) {
            Optional<User> userOptional = userRepository.findByEmail(email);
            User user;
            if (userOptional.isPresent()) {
                log.info("Success Handler: Updating existing user");
                user = userOptional.get();
                user.setFullName(name != null ? name : user.getFullName());
                user.setAuthProvider(AuthProvider.GOOGLE);
            } else {
                log.info("Success Handler: Creating new user");
                user = User.builder()
                        .email(email)
                        .fullName(name != null ? name : "Unknown")
                        .authProvider(AuthProvider.GOOGLE)
                        .role("USER")
                        .passwordHash("OAUTH2_USER")
                        .isActive(true)
                        .isDeleted(false)
                        .build();
            }
            userRepository.saveAndFlush(user);
            log.info("Success Handler: User {} saved with ID {}", email, user.getId());
        }

        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            log.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        String token = tokenProvider.generateToken(authentication);

        return UriComponentsBuilder.fromUriString(frontendUrl + "/login-success")
                .queryParam("token", token)
                .build().toUriString();
    }
}
