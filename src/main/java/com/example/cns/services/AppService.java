package com.example.cns.services;

import com.example.cns.dto.AppRequestDto;
import com.example.cns.dto.AppResponseDto;
import com.example.cns.entities.App;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppService {
    private final AppRepository appRepository;

    public AppResponseDto registerApp(@Valid AppRequestDto request) {
        log.info("Registering new application: {}", request.getName());

        if (appRepository.existsByName(request.getName())) {
            throw new com.example.cns.exception.DuplicateResourceException(
                    "Application with name '" + request.getName() + "' already exists.");
        }

        String currentAuditor = getCurrentAuditor();
        LocalDateTime now = LocalDateTime.now();

        App app = App.builder()
                .name(request.getName())
                .status("ACTIVE")
                .apiKey(UUID.randomUUID().toString())
                .isActive(true)
                .isDeleted(false)
                .createdAt(now) // Manually set for immediate response
                .createdBy(currentAuditor) // Manually set for immediate response
                .updatedAt(null) // Explicitly set to null for new registration
                .updatedBy(null) // Explicitly set to null for new registration
                .build();

        App savedApp = appRepository.saveAndFlush(app);
        log.info("Successfully registered app: {} with ID: {}", savedApp.getName(), savedApp.getId());
        return mapToDto(savedApp);
    }

    public org.springframework.data.domain.Page<AppResponseDto> getAllApps(
            Long id,
            String name,
            org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<App> apps = appRepository.findByFilters(id, name, pageable);
        return apps.map(this::mapToDto);
    }

    public AppResponseDto getAppById(Long id) {
        log.debug("Fetching app details for ID: {}", id);
        App app = appRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("App not found with id: " + id));
        return mapToDto(app);
    }

    public AppResponseDto updateApp(Long id, AppRequestDto request) {
        log.debug("Entering updateApp for App ID: {}", id);

        App app = appRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("App not found"));

        if (app.isDeleted()) {
            throw new com.example.cns.exception.InvalidOperationException("Cannot edit a DELETED app");
        }

        if (request.getName() == null && request.getStatus() == null) {
            throw new IllegalArgumentException("No fields to update");
        }

        if (request.getName() != null) {
            if (request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("App name cannot be empty");
            }
            if (appRepository.existsByName(request.getName()) && !app.getName().equals(request.getName())) {
                throw new com.example.cns.exception.DuplicateResourceException(
                        "App with name '" + request.getName() + "' already exists.");
            }
            app.setName(request.getName());
        }

        if (request.getStatus() != null) {
            String newStatus = request.getStatus().toUpperCase();
            if (!newStatus.equals("ACTIVE") && !newStatus.equals("ARCHIVED") && !newStatus.equals("DELETED")) {
                throw new IllegalArgumentException(
                        "Invalid status value. Allowed values: ACTIVE, ARCHIVED, DELETED");
            }

            if (newStatus.equals(app.getStatus())) {
                throw new com.example.cns.exception.InvalidOperationException("App is already in the requested state");
            }

            app.setStatus(newStatus);
            app.setActive("ACTIVE".equalsIgnoreCase(newStatus));
        }

        app.setUpdatedAt(LocalDateTime.now());
        app.setUpdatedBy(getCurrentAuditor());

        App savedApp = appRepository.save(app);

        log.info("App ID: {} updated successfully.", id);
        return mapToDto(savedApp);
    }

    public void deleteApp(Long id) {
        log.info("Attempting to delete app with ID: {}", id);
        App app = appRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("App not found with id: " + id));

        if (app.isDeleted()) {
            log.warn("Delete failed: Application with ID {} is already deleted", id);
            throw new com.example.cns.exception.InvalidOperationException("App already deleted");
        }

        app.setDeleted(true);
        app.setActive(false);
        app.setStatus("DELETED");
        app.setUpdatedAt(LocalDateTime.now());
        app.setUpdatedBy(getCurrentAuditor());
        // User feedback: do NOT nullify apiKey

        appRepository.save(app);
        log.info("App '{}' (ID: {}) has been successfully deleted. UpdatedBy: {}", app.getName(), id,
                app.getUpdatedBy());
    }

    private String getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "SYSTEM";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("name");
        }
        return authentication.getName();
    }

    private AppResponseDto mapToDto(App app) {
        return new AppResponseDto(
                app.getId(),
                app.getName(),
                app.getApiKey(),
                app.getStatus(),
                app.isActive(),
                app.isDeleted(),
                app.getCreatedAt(),
                app.getUpdatedAt(),
                app.getCreatedBy(),
                app.getUpdatedBy());
    }
}