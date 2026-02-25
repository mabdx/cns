package com.example.cns.services;

import com.example.cns.dto.AppRequestDto;
import com.example.cns.dto.AppResponseDto;
import com.example.cns.entities.App;
import com.example.cns.entities.Template; // Import Template entity
import com.example.cns.exception.InvalidOperationException;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;
import com.example.cns.repositories.TemplateRepository; // Import TemplateRepository
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import Transactional

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppService {
    private final AppRepository appRepository;
    private final TemplateRepository templateRepository; // Inject TemplateRepository
    private static final int MAX_APP_NAME_LENGTH = 100;

    @Transactional // Add transactional annotation for delete operation
    public AppResponseDto registerApp(@Valid AppRequestDto request) {
        log.info("Registering new application: {}", request.getName());

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("App name cannot be empty.");
        }
        if (request.getName().length() > MAX_APP_NAME_LENGTH) {
            throw new IllegalArgumentException("App name cannot exceed " + MAX_APP_NAME_LENGTH + " characters.");
        }
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

    public Page<AppResponseDto> getAllApps(
            Long id,
            String name,
            String status,
            Pageable pageable) {

        boolean includeDeleted = false;
        if (status != null && !status.trim().isEmpty()) {
            String upperStatus = status.toUpperCase();
            if (!Set.of("ACTIVE", "ARCHIVED", "DELETED").contains(upperStatus)) {
                throw new IllegalArgumentException("Invalid status value. Allowed values: ACTIVE, ARCHIVED, DELETED");
            }
            if ("DELETED".equalsIgnoreCase(upperStatus)) {
                includeDeleted = true;
            }
        }

        Page<App> apps = appRepository.findByFilters(id, name, status, includeDeleted, pageable);
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
            throw new InvalidOperationException("Cannot edit a DELETED app");
        }

        if (request.getName() != null && request.getName().length() > MAX_APP_NAME_LENGTH) {
            throw new IllegalArgumentException("App name cannot exceed " + MAX_APP_NAME_LENGTH + " characters.");
        }

        if (request.getName() == null && request.getStatus() == null) {
            throw new IllegalArgumentException("No fields to update");
        }

        boolean nameChanged = request.getName() != null && !request.getName().equals(app.getName());
        boolean statusChanged = request.getStatus() != null &&
                !request.getStatus().toUpperCase().equals(app.getStatus());

        if (!nameChanged && !statusChanged) {
            throw new InvalidOperationException("Nothing is changed");
        }

        if (request.getName() != null) {
            if (request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("App name cannot be empty");
            }
            if (nameChanged) {
                appRepository.findByName(request.getName()).ifPresent(existingApp -> {
                    if (!existingApp.getId().equals(app.getId())) {
                        throw new com.example.cns.exception.DuplicateResourceException(
                                "App with name '" + request.getName() + "' already exists.");
                    }
                });
                app.setName(request.getName());
            }
        }

        if (request.getStatus() != null) {
            String newStatus = request.getStatus().toUpperCase();

            if (newStatus.equals("DELETED")) {
                throw new InvalidOperationException("Use the DELETE endpoint to delete an application");
            }

            if (!Set.of("ACTIVE", "ARCHIVED").contains(newStatus)) {
                throw new IllegalArgumentException("Invalid status value. Allowed values: ACTIVE, ARCHIVED");
            }

            if (statusChanged) {
                app.setStatus(newStatus);
                switch (newStatus) {
                    case "ACTIVE":
                        app.setActive(true);
                        app.setDeleted(false);
                        break;
                    case "ARCHIVED":
                        app.setActive(false);
                        app.setDeleted(false);
                        break;
                }
            }
        }

        app.setUpdatedAt(LocalDateTime.now());
        app.setUpdatedBy(getCurrentAuditor());

        App savedApp = appRepository.save(app);

        log.info("App ID: {} updated successfully.", id);
        return mapToDto(savedApp);
    }

    @Transactional // Ensure this operation is transactional
    public void deleteApp(Long id) {
        log.info("Attempting to delete app with ID: {}", id);
        App app = appRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("App not found with id: " + id));

        if (app.isDeleted()) {
            log.warn("Delete failed: Application with ID {} is already deleted", id);
            throw new InvalidOperationException("App already deleted");
        }

        // Mark associated templates as deleted
        List<Template> templates = templateRepository.findByAppId(app.getId());
        for (Template template : templates) {
            template.setDeleted(true);
            template.setActive(false);
            template.setStatus("DELETED");
            template.setUpdatedAt(LocalDateTime.now());
            template.setUpdatedBy(getCurrentAuditor());
        }
        templateRepository.saveAll(templates); // Save all updated templates
        log.info("Marked {} templates as DELETED for app '{}' (ID: {}).", templates.size(), app.getName(), id);


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
