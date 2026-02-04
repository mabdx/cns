package com.example.cns.controllers;

import com.example.cns.dto.TemplateRequestDto;
import com.example.cns.dto.TemplateResponseDto;
import com.example.cns.services.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<TemplateResponseDto> create(@Valid @RequestBody TemplateRequestDto request) {
        log.info("Received request to create template: {}", request.getName());
        return ResponseEntity.ok(templateService.createTemplate(request));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<TemplateResponseDto> update(
            @PathVariable Long id,
            @Valid @RequestBody TemplateRequestDto request
    ) {
        log.info("Received request to update template ID: {}", id);
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<String> archive(@PathVariable Long id) {
        log.info("Received request to archive template ID: {}", id);
        templateService.archiveTemplate(id);
        return ResponseEntity.ok("Template archived successfully.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Received request to delete template ID: {}", id);
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filter")
    public ResponseEntity<List<TemplateResponseDto>> filter(
            @RequestParam Long appId,
            @RequestParam String status
    ) {
        log.info("Received filter request for App ID: {} and Status: {}", appId, status);
        return ResponseEntity.ok(templateService.getTemplatesByAppAndStatus(appId, status));
    }

    @PostMapping("/draft")
    public ResponseEntity<TemplateResponseDto> saveDraft(@Valid @RequestBody TemplateRequestDto request) {
        log.info("Received request to save draft: {}", request.getName());
        return ResponseEntity.ok(templateService.saveAsDraft(request));
    }
}