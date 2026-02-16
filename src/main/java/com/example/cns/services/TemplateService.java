package com.example.cns.services;

import com.example.cns.dto.TemplateRequestDto;
import com.example.cns.dto.TemplateResponseDto;
import com.example.cns.entities.App;
import com.example.cns.entities.Template;
import com.example.cns.entities.TemplateTag;
import com.example.cns.exception.InvalidOperationException;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;
import com.example.cns.repositories.TemplateRepository;
import com.example.cns.repositories.TemplateTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final AppRepository appRepository;
    private final TemplateTagRepository tagRepository;

    /**
     * 1. CREATE TEMPLATE
     * Handles creation and automatic tag extraction.
     */
    @Transactional
    public TemplateResponseDto createTemplate(TemplateRequestDto request) {
        log.debug("Entering createTemplate for App ID: {}", request.getAppId());

        App app = appRepository.findById(request.getAppId())
                .orElseThrow(() -> {
                    log.error("Create failed: App ID {} not found", request.getAppId());
                    return new ResourceNotFoundException("App not found");
                });

        if (!app.isActive()) {
            throw new InvalidOperationException("Cannot create template for an inactive application.");
        }

        if (templateRepository.existsByAppIdAndName(request.getAppId(), request.getName())) {
            throw new com.example.cns.exception.DuplicateResourceException(
                    "Template with name '" + request.getName() + "' already exists for this app.");
        }

        String currentAuditor = getCurrentAuditor();
        LocalDateTime now = LocalDateTime.now();

        Template template = Template.builder()
                .app(app)
                .name(request.getName())
                .subject(request.getSubject())
                .htmlBody(request.getHtmlBody())
                .status("ACTIVE")
                .isActive(true)
                .isDeleted(false)
                .createdAt(now)
                .createdBy(currentAuditor)
                .updatedAt(null)
                .updatedBy(null)
                .build();

        Template savedTemplate = templateRepository.saveAndFlush(template);

        // Logic: Scan HTML for {{tags}} and save to DB
        List<String> extractedTags = extractAndSaveTags(savedTemplate, request.getHtmlBody());

        log.info("Template '{}' (ID: {}) created successfully with {} tags.",
                savedTemplate.getName(), savedTemplate.getId(), extractedTags.size());

        return mapToDto(savedTemplate, extractedTags);
    }

    /**
     * 2. UPDATE TEMPLATE (PATCH)
     * Handles partial updates.
     */
    @Transactional
    public TemplateResponseDto updateTemplate(Long id, TemplateRequestDto request) {
        log.debug("Entering updateTemplate for Template ID: {}", id);

        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        if (template.isDeleted()) {
            throw new InvalidOperationException("Cannot edit a DELETED template");
        }

        if ("ARCHIVED".equalsIgnoreCase(template.getStatus())) {
            if (request.getStatus() == null) {
                throw new InvalidOperationException("Cannot edit properties of an ARCHIVED template. Only status can be changed.");
            }
            if (request.getName() != null || request.getSubject() != null || request.getHtmlBody() != null || request.getAppId() != null) {
                throw new InvalidOperationException("Cannot edit properties of an ARCHIVED template. Only status can be changed.");
            }
        }

        if (request.getName() == null && request.getSubject() == null &&
                request.getHtmlBody() == null && request.getStatus() == null && request.getAppId() == null) {
            throw new IllegalArgumentException("No fields to update");
        }

        // BUG_39: Validate appId if provided
        if (request.getAppId() != null) {
            App newApp = appRepository.findById(request.getAppId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Application not found with ID: " + request.getAppId()));

            if (newApp.isDeleted()) {
                throw new InvalidOperationException(
                        "Cannot update template with a deleted application");
            }

            if ("ARCHIVED".equalsIgnoreCase(newApp.getStatus())) {
                throw new InvalidOperationException(
                        "Cannot update template with an archived application");
            }

            template.setApp(newApp);
        }

        boolean contentChanged = false;

        if (request.getName() != null) {
            if (request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Template name cannot be empty");
            }
            if (request.getName().equals(template.getName())) {
                throw new InvalidOperationException("The new name is the same as the current name.");
            }
            if (templateRepository.existsByAppIdAndName(template.getApp().getId(), request.getName())) {
                throw new com.example.cns.exception.DuplicateResourceException(
                        "Template with name '" + request.getName() + "' already exists.");
            }
            template.setName(request.getName());
        }
        if (request.getSubject() != null) {
            if (request.getSubject().trim().isEmpty()) {
                throw new IllegalArgumentException("Subject cannot be empty");
            }
            if (request.getSubject().equals(template.getSubject())) {
                throw new InvalidOperationException("The new subject is the same as the current subject.");
            }
            template.setSubject(request.getSubject());
        }
        if (request.getHtmlBody() != null) {
            if (request.getHtmlBody().trim().isEmpty()) {
                throw new IllegalArgumentException("HTML Body cannot be empty if provided");
            }
            if (request.getHtmlBody().equals(template.getHtmlBody())) {
                throw new InvalidOperationException("The new HTML body is the same as the current one.");
            }
            template.setHtmlBody(request.getHtmlBody());
            contentChanged = true;
        }
        if (request.getStatus() != null) {
            String newStatus = request.getStatus().toUpperCase();
            if (newStatus.equals("DELETED")) {
                throw new InvalidOperationException(
                        "Cannot change status to DELETED via update. Use the delete endpoint instead.");
            }
            if (!newStatus.equals("ACTIVE") && !newStatus.equals("ARCHIVED") && !newStatus.equals("DRAFT")) {
                throw new IllegalArgumentException(
                        "Invalid status value. Allowed values for update: ACTIVE, ARCHIVED, DRAFT");
            }

            if (newStatus.equals(template.getStatus())) {
                throw new InvalidOperationException("Template is already in the requested status.");
            }

            template.setStatus(newStatus);
            switch (newStatus) {
                case "ACTIVE":
                    template.setActive(true);
                    template.setDeleted(false);
                    break;
                case "ARCHIVED":
                case "DRAFT":
                    template.setActive(false);
                    template.setDeleted(false);
                    break;
            }
        }

        // Set update timestamp manually since we removed @UpdateTimestamp
        template.setUpdatedAt(LocalDateTime.now());
        template.setUpdatedBy(getCurrentAuditor());

        Template savedTemplate = templateRepository.save(template);

        List<String> tags;
        if (contentChanged) {
            tagRepository.deleteByTemplateId(savedTemplate.getId());
            tags = extractAndSaveTags(savedTemplate, request.getHtmlBody());
        } else {
            tags = tagRepository.findByTemplateId(savedTemplate.getId()).stream().map(TemplateTag::getTagName).toList();
        }

        log.info("Template ID: {} updated successfully.", id);
        return mapToDto(savedTemplate, tags);
    }

    /**
     * 4. DELETE TEMPLATE (Soft Delete)
     */
    public void deleteTemplate(Long id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        if (template.isDeleted()) {
            log.warn("Delete failed: Template with ID {} is already deleted", id);
            throw new InvalidOperationException("Template already deleted");
        }

        template.setDeleted(true);
        template.setActive(false);
        template.setStatus("DELETED");
        template.setUpdatedAt(LocalDateTime.now());
        template.setUpdatedBy(getCurrentAuditor());
        templateRepository.save(template);
        log.info("Template ID: {} has been soft-deleted. UpdatedBy: {}", id, template.getUpdatedBy());
    }

    private String getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "SYSTEM";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("name");
        }
        return authentication.getName();
    }

    /**
     * 5. FILTER / SEARCH (With Pagination)
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TemplateResponseDto> getTemplates(Long appId, String status,
            String name,
            org.springframework.data.domain.Pageable pageable) {

        if (appId != null) {
            App app = appRepository.findById(appId)
                    .orElseThrow(() -> new ResourceNotFoundException("Application not found with ID: " + appId));

            if (app.isDeleted()) {
                throw new InvalidOperationException(
                        "Cannot filter templates for a deleted application");
            }
        }

        if (status != null && !status.trim().isEmpty()) {
            String upperStatus = status.toUpperCase();
            if (!Set.of("ACTIVE", "ARCHIVED", "DRAFT", "DELETED").contains(upperStatus)) {
                throw new IllegalArgumentException(
                        "Invalid status value. Allowed values: ACTIVE, ARCHIVED, DRAFT, DELETED");
            }
        }

        org.springframework.data.domain.Page<Template> templates = templateRepository.findByAppIdAndStatus(appId,
                status, name, pageable);

        return templates.map(t -> {
            List<String> tags = tagRepository.findByTemplateId(t.getId())
                    .stream()
                    .map(TemplateTag::getTagName)
                    .collect(Collectors.toList());
            return mapToDto(t, tags);
        });
    }

    /**
     * 6. SAVE AS DRAFT
     * Helper method.
     */
    public TemplateResponseDto saveAsDraft(TemplateRequestDto request) {
        log.info("Saving template as DRAFT for app: {}", request.getAppId());
        request.setStatus("DRAFT");
        return createTemplate(request);
    }

    // ==========================================
    // PRIVATE HELPER METHODS (The Core Logic)
    // ==========================================

    /**
     * Extracts strings inside {{ }} from HTML and saves them to the Tag Repository.
     */
    private List<String> extractAndSaveTags(Template template, String html) {
        List<String> tagsFound = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\{\\{(.+?)\\}\\}");
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String tagName = matcher.group(1).trim();
            tagsFound.add(tagName);

            tagRepository.save(TemplateTag.builder()
                    .template(template)
                    .tagName(tagName)
                    .build());
        }

        log.debug("Extracted {} tags for Template ID {}: {}", tagsFound.size(), template.getId(), tagsFound);
        return tagsFound;
    }

    /**
     * 7. GET TEMPLATE BY ID
     */
    @Transactional(readOnly = true)
    public TemplateResponseDto getTemplateById(Long id) {
        log.debug("Fetching template ID: {}", id);
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with ID: " + id));

        List<String> tags = tagRepository.findByTemplateId(id).stream()
                .map(TemplateTag::getTagName)
                .toList();

        return mapToDto(template, tags);
    }

    private TemplateResponseDto mapToDto(Template t, List<String> tags) {
        return TemplateResponseDto.builder()
                .id(t.getId())
                .appId(t.getApp().getId())
                .appName(t.getApp().getName())
                .name(t.getName())
                .htmlBody(t.getHtmlBody())
                .subject(t.getSubject())
                .status(t.getStatus())
                .detectedTags(tags)
                .isDeleted(t.isDeleted())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .createdBy(t.getCreatedBy())
                .updatedBy(t.getUpdatedBy())
                .build();
    }
}