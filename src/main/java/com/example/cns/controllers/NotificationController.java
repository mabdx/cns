package com.example.cns.controllers;

import com.example.cns.dto.NotificationRequestDto;
import com.example.cns.dto.NotificationResponseDto;
import com.example.cns.services.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody NotificationRequestDto request) {
        log.info("Received notification request");

        // The service now handles single, multiple, or no recipients automatically
        Map<String, Object> result = notificationService.sendNotification(request);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/logs")
    public ResponseEntity<List<NotificationResponseDto>> getAllNotifications() {
        log.info("Fetching all notifications");
        List<NotificationResponseDto> notifications = notificationService.getAllNotifications();
        return ResponseEntity.ok(notifications);
    }
}