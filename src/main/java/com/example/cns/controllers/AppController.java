package com.example.cns.controllers;

import com.example.cns.dto.AppRequestDto;
import com.example.cns.dto.AppResponseDto;
import com.example.cns.services.AppService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/api/apps")
@RequiredArgsConstructor
public class AppController {
    private final AppService appService;

    @PostMapping("/register")
    public ResponseEntity<java.util.Map<String, Object>> resister(@Valid @RequestBody AppRequestDto request) {
        AppResponseDto app = appService.registerApp(request);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Application created successfully");
        response.put("data", app);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<AppResponseDto>> getAllApps(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String name,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "id") org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(appService.getAllApps(id, name, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppResponseDto> getAppById(@PathVariable Long id) {
        return ResponseEntity.ok(appService.getAppById(id));
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<java.util.Map<String, Object>> archiveApp(@PathVariable Long id) {
        appService.archiveApp(id);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "App archived successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<java.util.Map<String, Object>> deleteApp(@PathVariable Long id) {
        appService.deleteApp(id);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "App deleted successfully");
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/unarchive")
    public ResponseEntity<java.util.Map<String, Object>> unarchiveApp(@PathVariable Long id) {
        appService.unarchiveApp(id);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "App unarchived successfully");
        return ResponseEntity.ok(response);
    }
}
