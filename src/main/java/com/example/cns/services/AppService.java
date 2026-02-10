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

import java.util.List;
import java.util.UUID;

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

        App app = App.builder()
                .name(request.getName())
                .status("ACTIVE")
                .apiKey(UUID.randomUUID().toString())
                .isActive(true)
                .isDeleted(false)
                .build();

        App savedApp = appRepository.save(app);
        log.info("Successfully registered app: {} with ID: {}", savedApp.getName(), savedApp.getId());
        return mapToDto(savedApp);
    }

    public List<AppResponseDto> getAllApps() {
        return appRepository.findAll().stream()
                .filter(app -> !app.isDeleted())
                .map(this::mapToDto)
                .toList();
    }

    public void deleteApp(Long id) {
        log.info("Attempting to delete app with ID: {}", id);
        App app = appRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("App not found with id: " + id));

        app.setDeleted(true);
        app.setActive(false);
        app.setStatus("DELETED");
        // User feedback: do NOT nullify apiKey

        appRepository.save(app);
        log.info("App '{}' (ID: {}) has been successfully deleted.", app.getName(), id);
    }

    public void archiveApp(Long id) {
        log.info("Attempting to archive app with ID: {}", id);

        App app = appRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Archive failed: App with ID {} not found", id);
                    return new ResourceNotFoundException("Cannot archive: App not found.");
                });

        if (app.isDeleted()) {
            throw new com.example.cns.exception.InvalidOperationException("Cannot archive a DELETED app.");
        }

        if ("ARCHIVED".equalsIgnoreCase(app.getStatus())) {
            log.warn("App with ID {} is already archived.", id);
            return;
        }

        app.setStatus("ARCHIVED");
        app.setActive(false);
        appRepository.save(app);

        log.info("App '{}' (ID: {}) has been successfully archived.", app.getName(), id);
    }

    public void unarchiveApp(Long id) {
        log.info("Attempting to unarchive app with ID: {}", id);
        App app = appRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("App not found with id: " + id));

        if (app.isDeleted()) {
            throw new com.example.cns.exception.InvalidOperationException("Cannot unarchive a DELETED app.");
        }

        app.setStatus("ACTIVE");
        app.setActive(true);
        appRepository.save(app);
        log.info("App '{}' (ID: {}) has been successfully unarchived.", app.getName(), id);
    }

    private AppResponseDto mapToDto(App app) {
        return new AppResponseDto(
                app.getId(),
                app.getName(),
                app.getApiKey(),
                app.getStatus(),
                app.isActive(),
                app.getCreatedAt());
    }
}
