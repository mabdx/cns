package com.example.cns.controllers;

import com.example.cns.dto.AppRequestDto;
import com.example.cns.dto.AppResponseDto;
import com.example.cns.services.AppService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
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
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<AppResponseDto>> getAllApps(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam Map<String, String> allParams,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable) {

        Set<String> allowedParams = Set.of("id", "name", "status", "page", "size", "sort");
        for (String param : allParams.keySet()) {
            if (!allowedParams.contains(param)) {
                throw new IllegalArgumentException("Invalid filter parameter: " + param);
            }
        }

        return ResponseEntity.ok(appService.getAllApps(id, name, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppResponseDto> getAppById(@PathVariable Long id) {
        return ResponseEntity.ok(appService.getAppById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateApp(
            @PathVariable Long id,
            @RequestBody AppRequestDto request) {
        AppResponseDto app = appService.updateApp(id, request);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "App updated successfully");
        response.put("data", app);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Map<String, Object>> deleteApp(@PathVariable Long id) {
        appService.deleteApp(id);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "App deleted successfully");
        return ResponseEntity.ok(response);
    }
}