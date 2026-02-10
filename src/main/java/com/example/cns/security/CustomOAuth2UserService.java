package com.example.cns.security;

import com.example.cns.entities.AuthProvider;
import com.example.cns.entities.User;
import com.example.cns.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        System.out.println("OAuth2 Login: " + email);
        if (email == null) {
            System.out.println("Email is null!");
        }

        Optional<User> userOptional = userRepository.findByEmail(email);

        User user;
        if (userOptional.isPresent()) {
            System.out.println("Updating existing user: " + email);
            user = userOptional.get();
            user.setFullName(name);
            user.setAuthProvider(AuthProvider.GOOGLE);
            userRepository.save(user);
        } else {
            System.out.println("Creating new user: " + email);
            try {
                user = User.builder()
                        .email(email)
                        .fullName(name != null ? name : "Unknown")
                        .authProvider(AuthProvider.GOOGLE)
                        .role("USER")
                        .passwordHash("OAUTH2_USER") // Dummy value to satisfy potential NOT NULL constraint
                        .isActive(true)
                        .isDeleted(false)
                        .build();
                userRepository.save(user);
                System.out.println("User saved successfully.");
            } catch (Exception e) {
                System.err.println("Error saving user: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return oAuth2User;
    }
}
