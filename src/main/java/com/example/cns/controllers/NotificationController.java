package com.example.cns.controllers;

import com.example.cns.dto.NotificationBulkRequestDto;
import com.example.cns.dto.NotificationHealthDto;
import com.example.cns.dto.NotificationRequestDto;
import com.example.cns.dto.NotificationResponseDto;
import com.example.cns.services.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(
            @Valid @RequestBody NotificationRequestDto request,
            @RequestParam Map<String, String> allParams) {
        if (!allParams.isEmpty()) {
            throw new IllegalArgumentException("Unexpected query parameters: " + allParams.keySet());
        }
        log.info("Received notification request");

        // The service now handles single, multiple, or no recipients automatically
        Map<String, Object> result = notificationService.sendNotification(request);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/send/bulk")
    public ResponseEntity<Map<String, Object>> sendBulk(
            @Valid @RequestBody NotificationBulkRequestDto request,
            @RequestParam Map<String, String> allParams) {
        if (!allParams.isEmpty()) {
            throw new IllegalArgumentException("Unexpected query parameters: " + allParams.keySet());
        }
        log.info("Received personalized bulk notification request");
        Map<String, Object> result = notificationService.sendBulkNotifications(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retry(
            @PathVariable Long id,
            @RequestParam Map<String, String> allParams) {
        if (!allParams.isEmpty()) {
            throw new IllegalArgumentException("Unexpected query parameters: " + allParams.keySet());
        }
        log.info("Received retry request for notification ID: {}", id);
        Map<String, Object> result = notificationService.retryNotification(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponseDto> getNotificationById(
            @PathVariable Long id,
            @RequestParam Map<String, String> allParams) {
        if (!allParams.isEmpty()) {
            throw new IllegalArgumentException("Unexpected query parameters: " + allParams.keySet());
        }
        log.info("Received request for notification details with ID: {}", id);
        return ResponseEntity.ok(notificationService.getNotificationById(id));
    }

    @GetMapping("/logs")
    public ResponseEntity<Page<NotificationResponseDto>> getAllNotifications(
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        if (page < 0) {
            throw new IllegalArgumentException("Page index must not be less than zero!");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Page size must not be less than one!");
        }

        log.info("Fetching filtered notification logs");
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sort);

        return ResponseEntity.ok(
                notificationService.getAllNotifications(appId, templateId, recipientEmail, subject, status, pageable));
    }

    @GetMapping("/health")
    public ResponseEntity<NotificationHealthDto> getHealthSummary(
            @RequestParam Map<String, String> allParams) {
        if (!allParams.isEmpty()) {
            throw new IllegalArgumentException("Unexpected query parameters: " + allParams.keySet());
        }
        log.info("Received request for notification health summary");
        return ResponseEntity.ok(notificationService.getHealthSummary());
    }
}