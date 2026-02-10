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
import java.util.HashMap;
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

    public Map<String, Object> sendNotification(NotificationRequestDto request) {
        log.info("Received notification request");

        // Case 1: No recipients provided
        if ((request.getRecipient() == null || request.getRecipient().isBlank()) &&
                (request.getRecipients() == null || request.getRecipients().isEmpty())) {
            log.warn("No recipients provided in request - handling internally");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", "At least one recipient must be provided");
            errorResponse.put("totalRecipients", 0);
            errorResponse.put("successCount", 0);
            errorResponse.put("failureCount", 0);
            return errorResponse;
        }

        // Prepare recipient list
        List<String> recipientList = new ArrayList<>();

        // Case 2: Single recipient provided
        if (request.getRecipient() != null && !request.getRecipient().isBlank()) {
            recipientList.add(request.getRecipient());
            log.debug("Single recipient mode: {}", request.getRecipient());
        }

        // Case 3: Multiple recipients provided
        if (request.getRecipients() != null && !request.getRecipients().isEmpty()) {
            recipientList.addAll(request.getRecipients());
            log.debug("Multiple recipients mode: {} recipients", request.getRecipients().size());
        }

        // Now process all recipients (whether 1 or many)
        return processNotifications(request.getApiKey(), request.getTemplateId(), recipientList,
                request.getPlaceholders());
    }

    private Map<String, Object> processNotifications(String apiKey, Long templateId, List<String> recipients,
            Map<String, String> placeholders) {
        log.info("Starting notification process for {} recipient(s)", recipients.size());

        List<String> successfulRecipients = new ArrayList<>();
        List<String> failedRecipients = new ArrayList<>();

        // 1. API Key Validation - throws 401 if invalid
        App app = appRepository.findByApiKey(apiKey)
                .orElseThrow(() -> {
                    log.error("Invalid API Key: {}", apiKey);
                    return new SecurityException("Invalid API Key");
                });

        // 2. Fetch Template - throws 404 if not found
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> {
                    log.error("Template not found with ID: {}", templateId);
                    return new ResourceNotFoundException("Template not found with ID: " + templateId);
                });

        // 3. INTEGRATION CHECK: Is App and Template Active?
        if (app.isDeleted() || !"ACTIVE".equalsIgnoreCase(app.getStatus())) {
            throw new IllegalStateException("Application is " + app.getStatus() + " or deleted, cannot send.");
        }

        if (!"ACTIVE".equalsIgnoreCase(template.getStatus())) {
            log.error("Template is not ACTIVE. Current status: {}", template.getStatus());
            throw new IllegalStateException("Template is " + template.getStatus() + ", cannot send.");
        }

        // 4. INTEGRATION CHECK: Strict Tag Validation - throws 400 if tags missing
        validateTags(template.getId(), placeholders);

        // 5. Process each recipient individually
        for (String recipient : recipients) {
            try {
                log.debug("Processing notification for recipient: {}", recipient);

                String finalSubject = resolve(template.getSubject(), placeholders);
                String finalBody = resolve(template.getHtmlBody(), placeholders);

                // Save as SENT for this recipient
                Notification notification = Notification.builder()
                        .template(template)
                        .recipientEmail(recipient)
                        .subject(finalSubject)
                        .body(finalBody)
                        .status("SENT")
                        .retryCount(0)
                        .createdBy(app.getName())
                        .build();

                notificationRepository.save(notification);
                successfulRecipients.add(recipient);
                log.info("Notification sent successfully for recipient: {}", recipient);

            } catch (Exception e) {
                failedRecipients.add(recipient);
                log.warn("Failed to send notification to recipient: {} - {}", recipient, e.getMessage());

                // Save as FAILED for this recipient
                // We preserve the body (if it was resolved) or use the template's body as a
                // fallback
                String attemptedBody = template.getHtmlBody();
                try {
                    attemptedBody = resolve(template.getHtmlBody(), placeholders);
                } catch (Exception resolveEx) {
                    log.warn("Could not resolve body for failed notification: {}", resolveEx.getMessage());
                }

                Notification notification = Notification.builder()
                        .template(template)
                        .recipientEmail(recipient)
                        .subject(template.getSubject())
                        .body(attemptedBody)
                        .status("FAILED")
                        .errorMessage(e.getMessage())
                        .retryCount(0)
                        .createdBy(app.getName())
                        .build();
                notificationRepository.save(notification);
            }
        }

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("status", failedRecipients.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS");
        response.put("totalRecipients", recipients.size());
        response.put("successCount", successfulRecipients.size());
        response.put("failureCount", failedRecipients.size());
        response.put("successfulRecipients", successfulRecipients);
        response.put("failedRecipients", failedRecipients);
        response.put("message", String.format("Processed %d recipient(s). %d succeeded, %d failed.",
                recipients.size(), successfulRecipients.size(), failedRecipients.size()));

        return response;
    }

    public Map<String, Object> retryNotification(Long notificationId) {
        log.info("Retrying notification with ID: {}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID: " + notificationId));

        if (!"FAILED".equalsIgnoreCase(notification.getStatus())) {
            throw new IllegalStateException("Only FAILED notifications can be retried.");
        }

        try {
            // Here you would call your actual sending logic (e.g., mailSender.send(...))
            // For now, we simulate success as per existing implementation

            notification.setStatus("SENT");
            notification.setErrorMessage(null);
            notification.setRetryCount(notification.getRetryCount() + 1);
            notificationRepository.save(notification);

            log.info("Notification {} retried successfully. New retry count: {}", notificationId,
                    notification.getRetryCount());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("message", "Notification retried successfully");
            response.put("notificationId", notificationId);
            response.put("retryCount", notification.getRetryCount());
            return response;

        } catch (Exception e) {
            log.error("Retry failed for notification {}: {}", notificationId, e.getMessage());

            notification.setRetryCount(notification.getRetryCount() + 1);
            notification.setErrorMessage("Retry failed: " + e.getMessage());
            notificationRepository.save(notification);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "FAILED");
            response.put("message", "Retry failed: " + e.getMessage());
            response.put("notificationId", notificationId);
            response.put("retryCount", notification.getRetryCount());
            return response;
        }
    }

    public void sendNotificationOld(NotificationRequestDto request) {
        log.debug("Starting notification process for recipient: {}", request.getRecipient());

        // We declare these outside the try block so we can use them in the 'catch'
        // block
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
    private void saveToHistory(App app, Template template, NotificationRequestDto request, String bodyContent,
            String status) {
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

        if (requiredTags.isEmpty())
            return;

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
        if (placeholders == null)
            return body;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            body = body.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return body;
    }

    public Map<String, Object> sendBulkNotifications(com.example.cns.dto.NotificationBulkRequestDto request) {
        log.info("Starting personalized bulk notification process for {} recipients", request.getRecipients().size());

        List<String> successfulRecipients = new ArrayList<>();
        List<String> failedRecipients = new ArrayList<>();

        App app = null;
        Template template = null;

        try {
            // 1. API Key Validation
            app = appRepository.findByApiKey(request.getApiKey())
                    .orElseThrow(() -> new SecurityException("Invalid API Key"));

            // 2. Fetch Template
            template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Template not found with ID: " + request.getTemplateId()));

            // 3. INTEGRATION CHECK: Is template Active?
            if (!"ACTIVE".equalsIgnoreCase(template.getStatus())) {
                throw new IllegalStateException("Template is " + template.getStatus() + ", cannot send.");
            }

            // 4. Validate Global Tags (if any)
            if (request.getGlobalPlaceholders() != null) {
                validateTags(template.getId(), request.getGlobalPlaceholders());
            }

            // 5. Process each recipient individually
            for (Map.Entry<String, Map<String, String>> entry : request.getRecipients().entrySet()) {
                String recipientEmail = entry.getKey();
                Map<String, String> personalizedPlaceholders = entry.getValue();

                try {
                    log.debug("Processing personalized notification for recipient: {}", recipientEmail);

                    // Merge global with personalized (personalized takes precedence)
                    Map<String, String> mergedPlaceholders = new HashMap<>();
                    if (request.getGlobalPlaceholders() != null) {
                        mergedPlaceholders.putAll(request.getGlobalPlaceholders());
                    }
                    if (personalizedPlaceholders != null) {
                        mergedPlaceholders.putAll(personalizedPlaceholders);
                    }

                    // Validate merged tags for this recipient
                    validateTags(template.getId(), mergedPlaceholders);

                    String finalSubject = resolve(template.getSubject(), mergedPlaceholders);
                    String finalBody = resolve(template.getHtmlBody(), mergedPlaceholders);

                    // Save as SENT for this recipient
                    Notification notification = Notification.builder()
                            .template(template)
                            .recipientEmail(recipientEmail)
                            .subject(finalSubject)
                            .body(finalBody)
                            .status("SENT")
                            .retryCount(0)
                            .createdBy(app.getName())
                            .build();

                    notificationRepository.save(notification);
                    successfulRecipients.add(recipientEmail);
                    log.info("Notification sent successfully for recipient: {}", recipientEmail);

                } catch (Exception e) {
                    failedRecipients.add(recipientEmail);
                    log.error("Failed to send personalized notification to recipient: {} - {}", recipientEmail,
                            e.getMessage());

                    // Save as FAILED for this recipient
                    Notification notification = Notification.builder()
                            .template(template)
                            .recipientEmail(recipientEmail)
                            .subject(template.getSubject())
                            .body("ERROR: " + e.getMessage())
                            .errorMessage(e.getMessage())
                            .status("FAILED")
                            .retryCount(0)
                            .createdBy(app.getName())
                            .build();
                    notificationRepository.save(notification);
                }
            }

        } catch (Exception e) {
            log.error("Bulk personalized notification process failed at validation stage: {}", e.getMessage());
            // If validation fails (API key/Template), we can't process any
            if (request.getRecipients() != null) {
                failedRecipients.addAll(request.getRecipients().keySet());
            }
            throw e; // Rethrow to let global handler handle it
        }

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("status", failedRecipients.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS");
        response.put("totalRecipients", request.getRecipients().size());
        response.put("successCount", successfulRecipients.size());
        response.put("failureCount", failedRecipients.size());
        response.put("successfulRecipients", successfulRecipients);
        response.put("failedRecipients", failedRecipients);
        response.put("message",
                String.format("Personalized bulk notification processing completed. %d succeeded, %d failed.",
                        successfulRecipients.size(), failedRecipients.size()));

        return response;
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