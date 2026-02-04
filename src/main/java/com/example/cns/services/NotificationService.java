package com.example.cns.services;

import com.example.cns.dto.NotificationRequestDto;
import com.example.cns.dto.NotificationResponseDto;
import com.example.cns.entities.*;
import com.example.cns.repositories.*;
import com.example.cns.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AppRepository appRepository;
    private final TemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;
    private final TemplateTagRepository tagRepository;

    public void sendNotification(NotificationRequestDto request) {
        log.debug("Starting notification process for recipient: {}", request.getRecipient());

        // We declare these outside the try block so we can use them in the 'catch' block
        App app = null;
        Template template = null;

        try {
            // 1. API Key Validation
            app = appRepository.findByApiKey(request.getApiKey())
                    .orElseThrow(() -> new SecurityException("Invalid API Key"));

            // 2. Fetch Template
            template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

            // 3. INTEGRATION CHECK: Is template Active?
            if (!"ACTIVE".equalsIgnoreCase(template.getStatus())) {
                throw new IllegalStateException("Template is " + template.getStatus() + ", cannot send.");
            }

            // 4. INTEGRATION CHECK: Strict Tag Validation
            // This will now THROW an exception if something is missing
            validateTags(template.getId(), request.getPlaceholders());

            // 5. Logic: Replace {{ }} with real values
            String finalBody = resolve(template.getHtmlBody(), request.getPlaceholders());

            // 6. SUCCESS: Save as SENT
            saveToHistory(app, template, request, finalBody, "SENT");
            log.info("Notification successfully logged as SENT. ID: {}", template.getId());

        } catch (Exception e) {
            // 7. FAILURE: Save as FAILED (The Health Requirement)
            log.error("Notification FAILED: {}", e.getMessage());

            // We can only save to DB if we successfully found the App and Template first.
            // If the App/Template was null (invalid key), we just throw the error.
            if (app != null && template != null) {
                saveToHistory(app, template, request, "ERROR: " + e.getMessage(), "FAILED");
            }
            throw e; // Re-throw so the Controller knows to return an error code
        }
    }

    // Helper method to handle both SENT and FAILED saves
    private void saveToHistory(App app, Template template, NotificationRequestDto request, String bodyContent, String status) {
        Notification notification = Notification.builder()
                .template(template)
                .recipientEmail(request.getRecipient())
                .subject(template.getSubject())
                .body(bodyContent) // Success = HTML, Failure = Error Message
                .status(status)
                .retryCount(0)
                .createdBy(app.getName())
                .build();

        notificationRepository.save(notification);
    }

    // STRICT validation: Throws exception if missing
    private void validateTags(Long templateId, Map<String, String> placeholders) {
        List<String> requiredTags = tagRepository.findByTemplateId(templateId).stream()
                .map(TemplateTag::getTagName)
                .toList();

        if (requiredTags.isEmpty()) return;

        List<String> missing = new ArrayList<>();
        for (String required : requiredTags) {
            if (placeholders == null || !placeholders.containsKey(required)) {
                missing.add(required);
            }
        }

        if (!missing.isEmpty()) {
            // This throws the error -> Catch block catches it -> Saves as FAILED
            throw new IllegalArgumentException("Missing required tags: " + missing);
        }
    }

    private String resolve(String body, Map<String, String> placeholders) {
        if (placeholders == null) return body;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            body = body.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return body;
    }

    public List<NotificationResponseDto> getAllNotifications() {
        log.debug("Fetching all notifications from database");
        List<Notification> notifications = notificationRepository.findAll();
        return notifications.stream()
                .map(this::convertToResponseDto)
                .toList();
    }

    private NotificationResponseDto convertToResponseDto(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .templateId(notification.getTemplate().getId())
                .recipientEmail(notification.getRecipientEmail())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .status(notification.getStatus())
                .retryCount(notification.getRetryCount())
                .createdAt(notification.getCreatedAt())
                .createdBy(notification.getCreatedBy())
                .build();
    }
}