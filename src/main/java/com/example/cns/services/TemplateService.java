package com.example.cns.services;

import com.example.cns.dto.TagUpdateRequestDto;
import com.example.cns.dto.TemplateRequestDto;
import com.example.cns.dto.TemplateResponseDto;
import com.example.cns.entities.App;
import com.example.cns.entities.Template;
import com.example.cns.entities.TemplateTag;
import com.example.cns.entities.TagDatatype;
import com.example.cns.exception.InvalidOperationException;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;
import com.example.cns.repositories.TemplateRepository;
import com.example.cns.repositories.TemplateTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    @Value("${app.validation.max-subject-length}")
    private int maxSubjectLength;

    @Value("${app.validation.max-body-length}")
    private int maxBodyLength;

    /**
     * 1. CREATE TEMPLATE
     * Handles creation and automatic tag extraction.
     */
    @Transactional
    public TemplateResponseDto createTemplate(TemplateRequestDto request) {
        log.debug("Entering createTemplate for App ID: {}", request.getAppId());

        // Programmatic validation for Create
        if (request.getAppId() == null) {
            throw new IllegalArgumentException("App ID is required");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Template name is required");
        }
        if (request.getSubject() == null || request.getSubject().trim().isEmpty()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (request.getHtmlBody() == null || request.getHtmlBody().trim().isEmpty()) {
            throw new IllegalArgumentException("HTML Body is required");
        }
        if (request.getSubject().length() > maxSubjectLength) {
            throw new IllegalArgumentException("Subject cannot exceed " + maxSubjectLength + " characters.");
        }
        if (request.getHtmlBody().length() > maxBodyLength) {
            throw new IllegalArgumentException("HTML body cannot exceed " + maxBodyLength + " characters.");
        }

        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus().toUpperCase() : "DRAFT";
        if (!Set.of("ACTIVE", "DRAFT").contains(status)) {
            throw new IllegalArgumentException("Status must be either ACTIVE or DRAFT.");
        }

        // Validate that ACTIVE status requires non-empty subject and htmlBody
        if ("ACTIVE".equalsIgnoreCase(status)) {
            if (request.getSubject().trim().isEmpty() || request.getHtmlBody().trim().isEmpty()) {
                throw new IllegalArgumentException("Template cannot be set to ACTIVE status with empty subject or body");
            }
        }

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

        String safeHtml = processAndValidateHtml(request.getHtmlBody());

        String currentAuditor = getCurrentAuditor();
        LocalDateTime now = LocalDateTime.now();

        Template template = Template.builder()
                .app(app)
                .name(request.getName())
                .subject(request.getSubject())
                .htmlBody(safeHtml)
                .status(status)
                .isActive("ACTIVE".equals(status))
                .isDeleted(false)
                .createdAt(now)
                .createdBy(currentAuditor)
                .updatedAt(null)
                .updatedBy(null)
                .build();

        Template savedTemplate = templateRepository.saveAndFlush(template);

        // FIX: Extract tags from BOTH subject and body
        extractAndSaveTags(savedTemplate, savedTemplate.getSubject(), savedTemplate.getHtmlBody());

        List<TemplateTag> savedTags = tagRepository.findByTemplateId(savedTemplate.getId());

        log.info("Template '{}' (ID: {}) created successfully with status {} and {} tags.",
                savedTemplate.getName(), savedTemplate.getId(), savedTemplate.getStatus(), savedTags.size());

        return mapToDto(savedTemplate, savedTags);
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
        
        if (request.getSubject() != null && request.getSubject().length() > maxSubjectLength) {
            throw new IllegalArgumentException("Subject cannot exceed " + maxSubjectLength + " characters.");
        }
        if (request.getHtmlBody() != null && request.getHtmlBody().length() > maxBodyLength) {
            throw new IllegalArgumentException("HTML body cannot exceed " + maxBodyLength + " characters.");
        }

        if ("ARCHIVED".equalsIgnoreCase(template.getStatus())) {
            if (request.getStatus() == null) {
                throw new InvalidOperationException(
                        "Cannot edit properties of an ARCHIVED template. Only status can be changed.");
            }
            if (request.getName() != null || request.getSubject() != null || request.getHtmlBody() != null
                    || request.getAppId() != null) {
                throw new InvalidOperationException(
                        "Cannot edit properties of an ARCHIVED template. Only status can be changed.");
            }
        }

        if (request.getAppId() != null) {
            throw new InvalidOperationException("App ID cannot be changed for an existing template");
        }

        boolean nameChanged = request.getName() != null && !request.getName().equals(template.getName());
        boolean subjectChanged = request.getSubject() != null && !request.getSubject().equals(template.getSubject());

        String safeHtml = request.getHtmlBody() != null ? processAndValidateHtml(request.getHtmlBody()) : null;
        boolean bodyChanged = safeHtml != null && !safeHtml.equals(template.getHtmlBody());

        String newStatus = request.getStatus() != null ? request.getStatus().toUpperCase() : null;
        boolean statusChanged = newStatus != null && !newStatus.equals(template.getStatus());

        // Centralized "Nothing is changed" check
        if ((request.getName() != null || request.getSubject() != null ||
                request.getHtmlBody() != null || request.getStatus() != null) &&
                !nameChanged && !subjectChanged && !bodyChanged && !statusChanged) {
            throw new InvalidOperationException("Nothing is changed");
        }

        if (request.getName() == null && request.getSubject() == null &&
                request.getHtmlBody() == null && request.getStatus() == null) {
            throw new IllegalArgumentException("No fields to update");
        }

        boolean contentChanged = false;

        if (request.getName() != null) {
            if (request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Template name cannot be empty");
            }
            if (nameChanged) {
                if (templateRepository.existsByAppIdAndName(template.getApp().getId(), request.getName())) {
                    throw new com.example.cns.exception.DuplicateResourceException(
                            "Template with name '" + request.getName() + "' already exists for this app.");
                }
                template.setName(request.getName());
            }
        }

        if (request.getSubject() != null) {
            if (request.getSubject().trim().isEmpty()) {
                throw new IllegalArgumentException("Subject cannot be empty");
            }
            if (subjectChanged) {
                template.setSubject(request.getSubject());
                contentChanged = true;
            }
        }

        if (request.getHtmlBody() != null) {
            if (bodyChanged) {
                template.setHtmlBody(safeHtml);
                contentChanged = true;
            }
        }

        if (request.getStatus() != null) {
            if ("DELETED".equalsIgnoreCase(request.getStatus())) {
                throw new InvalidOperationException(
                        "Cannot change status to DELETED via update. Use the delete endpoint instead.");
            }
            if (statusChanged) {
                // Validate that ACTIVE status requires non-empty subject and body
                if ("ACTIVE".equalsIgnoreCase(newStatus)) {
                    String checkSubject = request.getSubject() != null ? request.getSubject() : template.getSubject();
                    String checkBody = request.getHtmlBody() != null ? request.getHtmlBody() : template.getHtmlBody();
                    if (checkSubject == null || checkSubject.trim().isEmpty() ||
                        checkBody == null || checkBody.trim().isEmpty()) {
                        throw new IllegalArgumentException("Template cannot be set to ACTIVE status with empty subject or body");
                    }
                }
                template.setStatus(newStatus);
                switch (newStatus) {
                    case "ACTIVE":
                        template.setActive(true);
                        template.setDeleted(false);
                        break;
                    case "DRAFT":
                    case "ARCHIVED":
                        template.setActive(false);
                        template.setDeleted(false);
                        break;
                }
            }
        }

        template.setUpdatedAt(LocalDateTime.now());
        template.setUpdatedBy(getCurrentAuditor());

        Template savedTemplate = templateRepository.save(template);

        List<TemplateTag> tags;
        if (contentChanged) {
            tagRepository.deleteByTemplateId(savedTemplate.getId());
            // FIX: Pass BOTH subject and body for tag extraction
            extractAndSaveTags(savedTemplate, savedTemplate.getSubject(), savedTemplate.getHtmlBody());
            tags = tagRepository.findByTemplateId(savedTemplate.getId());
        } else {
            tags = tagRepository.findByTemplateId(savedTemplate.getId());
        }

        log.info("Template ID: {} updated successfully.", id);
        return mapToDto(savedTemplate, tags);
    }

    /**
     * Get tags for a specific template
     */
    @Transactional(readOnly = true)
    public List<TemplateResponseDto.TagInfo> getTemplateTags(Long id) {
        log.debug("Fetching tags for template ID: {}", id);
        if (!templateRepository.existsById(id)) {
            throw new ResourceNotFoundException("Template not found with ID: " + id);
        }

        List<TemplateTag> tags = new ArrayList<>();
        try {
            tags = tagRepository.findByTemplateId(id);
        } catch (Exception e) {
            log.error("Failed to load tags for Template ID {}: {}. Possibly corrupted data in database.",
                    id, e.getMessage());
        }

        return tags.stream()
                .map(tag -> TemplateResponseDto.TagInfo.builder()
                        .tagName(tag.getTagName())
                        .datatype(tag.getDatatype().name())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Update datatypes for multiple tags of a template
     */
    @Transactional
    public TemplateResponseDto updateTemplateTags(Long id, TagUpdateRequestDto request) {
        log.debug("Updating tag types for template ID: {}", id);

        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        List<TemplateTag> existingTags = tagRepository.findByTemplateId(id);
        Map<String, TemplateTag> tagMap = existingTags.stream()
                .collect(Collectors.toMap(TemplateTag::getTagName, t -> t));

        if (request.getTagTypes() != null) {
            for (Map.Entry<String, String> entry : request.getTagTypes().entrySet()) {
                String tagName = entry.getKey();
                TagDatatype enumType;
                try {
                    enumType = TagDatatype.valueOf(entry.getValue().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Invalid datatype: " + entry.getValue() + ". Allowed: STRING, NUMBER, BOOLEAN");
                }

                TemplateTag tag = tagMap.get(tagName);
                if (tag == null) {
                    throw new ResourceNotFoundException("Tag '" + tagName + "' not found in this template");
                }
                tag.setDatatype(enumType);
                tagRepository.save(tag);
            }
        }

        return mapToDto(template, tagRepository.findByTemplateId(id));
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

        boolean includeDeleted = false;
        if (status != null && !status.trim().isEmpty()) {
            String upperStatus = status.toUpperCase();
            if (!Set.of("ACTIVE", "ARCHIVED", "DRAFT", "DELETED").contains(upperStatus)) {
                throw new IllegalArgumentException(
                        "Invalid status value. Allowed values: ACTIVE, ARCHIVED, DRAFT, DELETED");
            }
            if ("DELETED".equalsIgnoreCase(upperStatus)) {
                includeDeleted = true;
            }
        }

        org.springframework.data.domain.Page<Template> templates = templateRepository.findByAppIdAndStatus(appId,
                status, name, includeDeleted, pageable);

        return templates.map(t -> {
            List<TemplateTag> tags = new ArrayList<>();
            try {
                tags = tagRepository.findByTemplateId(t.getId());
            } catch (Exception e) {
                log.error("Failed to load tags for Template ID {}: {}. Possibly corrupted data in database.",
                        t.getId(), e.getMessage());
            }
            return mapToDto(t, tags);
        });
    }

    // ==========================================
    // PRIVATE HELPER METHODS (The Core Logic)
    // ==========================================

    private String processAndValidateHtml(String html) {
        if (html == null || html.trim().isEmpty()) {
            throw new IllegalArgumentException("HTML Body cannot be empty");
        }
        // Check if the input is likely plain text
        if (!html.contains("<") && !html.contains(">")) {
            // Convert plain text to HTML, preserving line breaks
            return "<p>" + html.replace("\n", "<br>") + "</p>";
        } else {
            // It seems to be HTML, so validate and sanitize it
            if (!Jsoup.isValid(html, Safelist.relaxed())) { // Changed from Safelist.basic() to Safelist.relaxed()
                throw new IllegalArgumentException("The provided HTML body is not well-formed.");
            }
            return Jsoup.clean(html, Safelist.relaxed()); // Changed from Safelist.basic() to Safelist.relaxed()
        }
    }

    /**
     * Extracts strings inside {{ }} from both subject and HTML and saves them to
     * the Tag Repository.
     */
    private List<String> extractAndSaveTags(Template template, String subject, String html) {
        Set<String> tagsFound = new HashSet<>();
        Pattern pattern = Pattern.compile("\\{\\{(.+?)\\}\\}");

        if (subject != null) {
            Matcher matcher = pattern.matcher(subject);
            while (matcher.find()) {
                tagsFound.add(matcher.group(1).trim());
            }
        }

        if (html != null) {
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                tagsFound.add(matcher.group(1).trim());
            }
        }

        for (String tagName : tagsFound) {
            if (tagName == null || tagName.trim().isEmpty()) {
                continue;
            }
            tagRepository.save(TemplateTag.builder()
                    .template(template)
                    .tagName(tagName)
                    .datatype(TagDatatype.STRING)
                    .build());
        }

        log.debug("Extracted {} unique tags for Template ID {}: {}", tagsFound.size(), template.getId(), tagsFound);
        return new ArrayList<>(tagsFound);
    }

    /**
     * 7. GET TEMPLATE BY ID
     */
    @Transactional(readOnly = true)
    public TemplateResponseDto getTemplateById(Long id) {
        log.debug("Fetching template ID: {}", id);
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with ID: " + id));

        List<TemplateTag> tags = tagRepository.findByTemplateId(id);

        return mapToDto(template, tags);
    }

    private TemplateResponseDto mapToDto(Template t, List<TemplateTag> tags) {
        List<TemplateResponseDto.TagInfo> tagInfos = tags.stream()
                .map(tag -> TemplateResponseDto.TagInfo.builder()
                        .tagName(tag.getTagName())
                        .datatype(tag.getDatatype().name())
                        .build())
                .collect(Collectors.toList());

        return TemplateResponseDto.builder()
                .id(t.getId())
                .appId(t.getApp().getId())
                .appName(t.getApp().getName())
                .name(t.getName())
                .htmlBody(t.getHtmlBody())
                .subject(t.getSubject())
                .status(t.getStatus())
                .detectedTags(tagInfos)
                .isDeleted(t.isDeleted())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .createdBy(t.getCreatedBy())
                .updatedBy(t.getUpdatedBy())
                .build();
    }
}
