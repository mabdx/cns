package com.example.cns.services;

import com.example.cns.dto.NotificationBulkRequestDto;
import com.example.cns.dto.NotificationHealthDto;
import com.example.cns.dto.NotificationRequestDto;
import com.example.cns.dto.NotificationResponseDto;
import com.example.cns.entities.App;
import com.example.cns.entities.Notification;
import com.example.cns.entities.Template;
import com.example.cns.entities.TemplateTag;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;
import com.example.cns.repositories.NotificationRepository;
import com.example.cns.repositories.TemplateRepository;
import com.example.cns.repositories.TemplateTagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

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

        if ((request.getRecipient() == null || request.getRecipient().isBlank()) &&
                (request.getRecipients() == null || request.getRecipients().isEmpty())) {
            log.warn("No recipients provided in request");
            throw new IllegalArgumentException("At least one recipient must be provided");
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
        // Ensure unique recipients (No repeated users)
        // Check for duplicates before filtering
        List<String> validRecipients = recipientList.stream()
                .filter(Objects::nonNull)
                .filter(r -> !r.isBlank())
                .toList();

        if (validRecipients.stream().distinct().count() < validRecipients.size()) {
            throw new IllegalArgumentException("Duplicate emails are not allowed in the request.");
        }

        // Ensure unique recipients (No repeated users)
        List<String> uniqueRecipients = recipientList.stream()
                .filter(Objects::nonNull)
                .filter(r -> !r.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (uniqueRecipients.isEmpty()) {
            log.warn("No valid recipients after filtering duplicates and blanks");
            throw new IllegalArgumentException("No valid recipients provided");
        }

        return processNotifications(request.getApiKey(), request.getTemplateId(), uniqueRecipients,
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

        // 2.5 Ownership Check: Does template belong to this app?
        if (template.getApp() == null || !template.getApp().getId().equals(app.getId())) {
            log.error(
                    "Security Breach Attempt: App '{}' (ID: {}) tried to use Template '{}' (ID: {}) which belongs to App '{}' (ID: {})",
                    app != null ? app.getName() : "N/A",
                    app != null ? app.getId() : "N/A",
                    template != null ? template.getName() : "N/A",
                    templateId,
                    template != null && template.getApp() != null ? template.getApp().getName() : "N/A",
                    template != null && template.getApp() != null ? template.getApp().getId() : "N/A");
            throw new SecurityException("Template ID " + templateId + " does not belong to this application.");
        }

        // 3. INTEGRATION CHECK: Is App and Template Active?
        if (app.isDeleted() || !"ACTIVE".equalsIgnoreCase(app.getStatus())) {
            throw new IllegalStateException("Application is " + app.getStatus() + " or deleted, cannot send.");
        }

        if (!"ACTIVE".equalsIgnoreCase(template.getStatus())) {
            throw new IllegalStateException("Template is not active. Current status: " + template.getStatus());
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
                        .app(app)
                        .recipientEmail(recipient)
                        .subject(finalSubject)
                        .body(finalBody)
                        .status("SENT")
                        .retryCount(0)
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
                        .app(app)
                        .recipientEmail(recipient)
                        .subject(template.getSubject())
                        .body(attemptedBody)
                        .status("FAILED")
                        .errorMessage(e.getMessage())
                        .retryCount(0)
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

    // Helper method to handle both SENT and FAILED saves
    private void saveToHistory(App app, Template template, NotificationRequestDto request, String bodyContent,
            String status) {
        Notification notification = Notification.builder()
                .template(template)
                .app(app)
                .recipientEmail(request.getRecipient())
                .subject(template.getSubject())
                .body(bodyContent) // Success = HTML, Failure = Error Message
                .status(status)
                .retryCount(0)
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
            String value = (placeholders != null) ? placeholders.get(required) : null;
            if (value == null || value.isBlank()) {
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
        if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
            log.warn("Empty bulk recipients map provided");
            throw new IllegalArgumentException("At least one recipient must be provided");
        }

        // Check for duplicate emails and validate format
        List<String> recipientEmails = request.getRecipients().stream()
                .map(NotificationBulkRequestDto.BulkRecipient::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .toList();

        if (recipientEmails.isEmpty()) {
            throw new IllegalArgumentException("No valid recipient emails provided.");
        }
        Set<String> uniqueEmails = new HashSet<>(recipientEmails);
        if (uniqueEmails.size() < recipientEmails.size()) {
            throw new IllegalArgumentException("Duplicate emails are not allowed in bulk requests.");
        }

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

            // 2.5 Ownership Check: Does template belong to this app?
            if (template.getApp() == null || !template.getApp().getId().equals(app.getId())) {
                log.error(
                        "Security Breach Attempt (Bulk): App '{}' (ID: {}) tried to use Template '{}' (ID: {}) which belongs to App '{}' (ID: {})",
                        app != null ? app.getName() : "N/A",
                        app != null ? app.getId() : "N/A",
                        template != null ? template.getName() : "N/A",
                        request.getTemplateId(),
                        template != null && template.getApp() != null ? template.getApp().getName() : "N/A",
                        template != null && template.getApp() != null ? template.getApp().getId() : "N/A");
                throw new SecurityException(
                        "Template ID " + request.getTemplateId() + " does not belong to this application.");
            }

            // 3. INTEGRATION CHECK: Is template Active?
            if (!"ACTIVE".equalsIgnoreCase(template.getStatus())) {
                throw new IllegalStateException("Template is not active. Current status: " + template.getStatus());
            }

            // Pre-validation: Check all recipients for missing params BEFORE sending any
            for (NotificationBulkRequestDto.BulkRecipient recipient : request.getRecipients()) {
                Map<String, String> mergedPlaceholders = new HashMap<>();
                if (request.getGlobalPlaceholders() != null) {
                    mergedPlaceholders.putAll(request.getGlobalPlaceholders());
                }
                if (recipient.getPlaceholders() != null) {
                    mergedPlaceholders.putAll(recipient.getPlaceholders());
                }
                validateTags(template.getId(), mergedPlaceholders);
            }

            // 4. Process each recipient individually
            for (NotificationBulkRequestDto.BulkRecipient recipient : request.getRecipients()) {
                String recipientEmail = recipient.getEmail();
                // (Null checks already performed in validation step above)

                Map<String, String> personalizedPlaceholders = recipient.getPlaceholders();

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
                            .app(app)
                            .recipientEmail(recipientEmail)
                            .subject(finalSubject)
                            .body(finalBody)
                            .status("SENT")
                            .retryCount(0)
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
                            .app(app)
                            .recipientEmail(recipientEmail)
                            .subject(template.getSubject())
                            .body("ERROR: " + e.getMessage())
                            .errorMessage(e.getMessage())
                            .status("FAILED")
                            .retryCount(0)
                            .build();
                    notificationRepository.save(notification);
                }
            }

        } catch (Exception e) {
            log.error("Bulk personalized notification process failed at validation stage: {}", e.getMessage());
            // If validation fails (API key/Template), we can't process any
            if (request.getRecipients() != null) {
                failedRecipients.addAll(request.getRecipients().stream()
                        .map(NotificationBulkRequestDto.BulkRecipient::getEmail)
                        .toList());
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

    public Page<NotificationResponseDto> getAllNotifications(Long appId, Long templateId, String recipientEmail,
            String status,
            Pageable pageable) {
        log.debug("Fetching notifications with filters - AppId: {}, TemplateId: {}, Email: {}, Status: {}", appId,
                templateId,
                recipientEmail, status);

        if (templateId != null && !templateRepository.existsById(templateId)) {
            throw new ResourceNotFoundException("Template not found with ID: " + templateId);
        }

        if (status != null && !status.isBlank()) {
            List<String> validStatuses = List.of("SENT", "FAILED");
            if (!validStatuses.contains(status.toUpperCase())) {
                throw new IllegalArgumentException("Invalid status: " + status + ". Allowed values: SENT, FAILED");
            }
        }

        Page<Notification> notifications = notificationRepository.findByFilters(appId, templateId, recipientEmail,
                status,
                pageable);
        return notifications.map(this::convertToResponseDto);
    }

    public NotificationHealthDto getHealthSummary() {
        long successful = notificationRepository.countByStatus("SENT");
        long failed = notificationRepository.countByStatus("FAILED");
        long total = successful + failed;

        if (total == 0) {
            return new NotificationHealthDto(0, 0, "-");
        }

        double percentage = ((double) successful / total) * 100.0;
        String healthPercentage = new DecimalFormat("#.##").format(percentage) + "%";

        return new NotificationHealthDto(successful, failed, healthPercentage);
    }

    private NotificationResponseDto convertToResponseDto(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .templateId(notification.getTemplate() != null ? notification.getTemplate().getId() : null)
                .recipientEmail(notification.getRecipientEmail())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .status(notification.getStatus())
                .retryCount(notification.getRetryCount())
                .createdAt(notification.getCreatedAt())
                .appName(notification.getApp() != null ? notification.getApp().getName() : "N/A")
                .build();
    }
}