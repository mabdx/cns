package com.example.cns.services;

import com.example.cns.dto.UserRequestDto;
import com.example.cns.dto.UserResponseDto;
import com.example.cns.entities.User;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder; // Use interface, not class
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponseDto createUser(UserRequestDto request) {
        log.debug("Attempting to create user with email: {}", request.getEmail());

        // 1. Validation: Check duplicates
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            log.error("Creation failed: Email {} already exists", request.getEmail());
            throw new IllegalArgumentException("Email already in use");
        }

        // 2. Build User
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("USER") // Default role
                .isActive(true)
                .isDeleted(false)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());

        return mapToDto(savedUser);
    }

    public void deleteUser(Long userId) {
        log.debug("Attempting soft-delete for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setDeleted(true);
        user.setActive(false);
        userRepository.save(user);

        log.info("User ID: {} has been deactivated (soft deleted)", userId);
    }

    // Helper to get user details (useful for testing)
    public UserResponseDto getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToDto(user);
    }

    private UserResponseDto mapToDto(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.isActive())
                .build();
    }
}