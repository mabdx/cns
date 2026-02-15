package com.example.cns.controllers;

import com.example.cns.dto.NotificationRequestDto;
import com.example.cns.dto.NotificationBulkRequestDto;
import com.example.cns.dto.NotificationResponseDto;
import com.example.cns.services.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody NotificationRequestDto request) {
        log.info("Received notification request");

        // The service now handles single, multiple, or no recipients automatically
        Map<String, Object> result = notificationService.sendNotification(request);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/send/bulk")
    public ResponseEntity<Map<String, Object>> sendBulk(@Valid @RequestBody NotificationBulkRequestDto request) {
        log.info("Received personalized bulk notification request");
        Map<String, Object> result = notificationService.sendBulkNotifications(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retry(@PathVariable Long id) {
        log.info("Received retry request for notification ID: {}", id);
        Map<String, Object> result = notificationService.retryNotification(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/logs")
    public ResponseEntity<Page<NotificationResponseDto>> getAllNotifications(
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        log.info("Fetching filtered notification logs");
        return ResponseEntity.ok(notificationService.getAllNotifications(appId, templateId, recipientEmail, status, pageable));
    }
}