package com.example.cns.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }

            Object principal = authentication.getPrincipal();

            // When using JWT, Northern lights authentication is often a String (the email)
            // or we might have stored more info.
            if (principal instanceof OAuth2User oAuth2User) {
                String name = oAuth2User.getAttribute("name");
                return Optional.ofNullable(name);
            }

            // If it's our JWT authentication, we might just have the name or email.
            // In JwtAuthenticationFilter, we set email as the principal.
            // If we want the name from the JWT, we'd need to store it in the Authentication
            // object.
            // For now, if it's a String, we'll try to find if we can get the name.

            return Optional.ofNullable(authentication.getName());
        };
    }
}
