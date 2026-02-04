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

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    public ResponseEntity<String> send(@Valid @RequestBody NotificationRequestDto request) {
        log.info("Received notification request for recipient: {}", request.getRecipient());

        // This triggers your entire logic (validation, tag replacement, DB saving)
        notificationService.sendNotification(request);

        return ResponseEntity.ok("Notification processed and logged successfully.");
    }

    @GetMapping("/logs")
    public ResponseEntity<List<NotificationResponseDto>> getAllNotifications() {
        log.info("Fetching all notifications");
        List<NotificationResponseDto> notifications = notificationService.getAllNotifications();
        return ResponseEntity.ok(notifications);
    }
}