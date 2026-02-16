package com.example.cns.controllers;

import com.example.cns.dto.TemplateRequestDto;
import com.example.cns.dto.TemplateResponseDto;
import com.example.cns.services.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {
    private final TemplateService templateService;

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody TemplateRequestDto request) {
        log.info("Received request to create template: {}", request.getName());
        TemplateResponseDto template = templateService.createTemplate(request);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Template created successfully");
        response.put("data", template);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponseDto> getById(@PathVariable Long id) {
        log.info("Received request to fetch template ID: {}", id);
        return ResponseEntity.ok(templateService.getTemplateById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody TemplateRequestDto request) {
        log.info("Received request to update template ID: {}", id);
        TemplateResponseDto template = templateService.updateTemplate(id, request);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Template updated successfully");
        response.put("data", template);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        log.info("Received request to delete template ID: {}", id);
        templateService.deleteTemplate(id);
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Template soft-deleted successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<TemplateResponseDto>> getAll(
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String name,
            @RequestParam Map<String, String> allParams,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable) {
        
        Set<String> allowedParams = Set.of("appId", "status", "name", "page", "size", "sort");
        for (String param : allParams.keySet()) {
            if (!allowedParams.contains(param)) {
                throw new IllegalArgumentException("Invalid filter parameter: " + param);
            }
        }

        log.info("Received request to get/filter templates. App ID: {}, Status: {}, Name: {}", appId, status, name);
        return ResponseEntity.ok(templateService.getTemplates(appId, status, name, pageable));
    }
}