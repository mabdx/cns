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

@Slf4j
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {
    private final TemplateService templateService;

    @PostMapping("/create")
    public ResponseEntity<java.util.Map<String, Object>> create(@Valid @RequestBody TemplateRequestDto request) {
        log.info("Received request to create template: {}", request.getName());
        TemplateResponseDto template = templateService.createTemplate(request);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
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
    public ResponseEntity<java.util.Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody TemplateRequestDto request) {
        log.info("Received request to update template ID: {}", id);
        TemplateResponseDto template = templateService.updateTemplate(id, request);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Template updated successfully");
        response.put("data", template);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<java.util.Map<String, Object>> delete(@PathVariable Long id) {
        log.info("Received request to delete template ID: {}", id);
        templateService.deleteTemplate(id);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Template soft-deleted successfully");
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<java.util.Map<String, Object>> activate(@PathVariable Long id) {
        log.info("Received request to activate template ID: {}", id);
        templateService.activateTemplate(id);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "Template activated successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<TemplateResponseDto>> getAll(
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String name,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable) {
        log.info("Received request to get/filter templates. App ID: {}, Status: {}, Name: {}", appId, status, name);
        return ResponseEntity.ok(templateService.getTemplates(appId, status, name, pageable));
    }

    @PostMapping("/draft")
    public ResponseEntity<TemplateResponseDto> saveDraft(@Valid @RequestBody TemplateRequestDto request) {
        log.info("Received request to save draft: {}", request.getName());
        return ResponseEntity.ok(templateService.saveAsDraft(request));
    }
}