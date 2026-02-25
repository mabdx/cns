package com.example.cns.controllers;

import com.example.cns.dto.AppRequestDto;
import com.example.cns.dto.AppResponseDto;
import com.example.cns.services.AppService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class AppController {
    private final AppService appService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> resister(@Valid @RequestBody AppRequestDto request) {
        AppResponseDto app = appService.registerApp(request);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Application created successfully");
        response.put("data", app);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<AppResponseDto>> getAllApps(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam Map<String, String> allParams,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Set<String> allowedParams = Set.of("id", "name", "status", "page", "size", "sort");
        for (String param : allParams.keySet()) {
            if (!allowedParams.contains(param)) {
                throw new IllegalArgumentException("Invalid filter parameter: " + param);
            }
        }

        if (allParams.containsKey("page")) {
            if (Integer.parseInt(allParams.get("page")) < 0) {
                throw new IllegalArgumentException("Page number must not be less than zero.");
            }
        }
        if (allParams.containsKey("size")) {
            if (Integer.parseInt(allParams.get("size")) < 1) {
                throw new IllegalArgumentException("Page size must not be less than one.");
            }
        }

        return ResponseEntity.ok(appService.getAllApps(id, name, status, pageable));
    }
    // Sorting example: sort=createdAt,asc or sort=createdAt,desc (default is createdAt,desc)
    // Status values: ACTIVE, ARCHIVED, DELETED

    @GetMapping("/{id}")
    public ResponseEntity<AppResponseDto> getAppById(
            @PathVariable Long id,
            @RequestParam Map<String, String> allParams) {
        if (!allParams.isEmpty()) {
            throw new IllegalArgumentException("Unexpected query parameters: " + allParams.keySet());
        }
        return ResponseEntity.ok(appService.getAppById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateApp(
            @PathVariable Long id,
            @RequestBody AppRequestDto request,
            @RequestParam Map<String, String> allParams) {
        if (!allParams.isEmpty()) {
            throw new IllegalArgumentException("Unexpected query parameters: " + allParams.keySet());
        }
        AppResponseDto app = appService.updateApp(id, request);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "App updated successfully");
        response.put("data", app);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteApp(
            @PathVariable Long id,
            @RequestParam Map<String, String> allParams) {
        if (!allParams.isEmpty()) {
            throw new IllegalArgumentException("Unexpected query parameters: " + allParams.keySet());
        }
        appService.deleteApp(id);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "App deleted successfully");
        return ResponseEntity.ok(response);
    }
}