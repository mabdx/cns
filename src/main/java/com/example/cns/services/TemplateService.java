package com.example.cns.services;

import com.example.cns.dto.TemplateRequestDto;
import com.example.cns.dto.TemplateResponseDto;
import com.example.cns.entities.App;
import com.example.cns.entities.Template;
import com.example.cns.entities.TemplateTag;
import com.example.cns.exception.ResourceNotFoundException;
import com.example.cns.repositories.AppRepository;
import com.example.cns.repositories.TemplateRepository;
import com.example.cns.repositories.TemplateTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

        if (app.isDeleted() || !"ACTIVE".equalsIgnoreCase(app.getStatus())) {
            throw new com.example.cns.exception.InvalidOperationException(
                    "Cannot create template for a DELETED or non-ACTIVE application.");
        }

        if (templateRepository.existsByAppIdAndName(request.getAppId(), request.getName())) {
            throw new com.example.cns.exception.DuplicateResourceException(
                    "Template with name '" + request.getName() + "' already exists for this app.");
        }

        Template template = Template.builder()
                .app(app)
                .name(request.getName())
                .subject(request.getSubject())
                .htmlBody(request.getHtmlBody())
                .status(request.getStatus() != null ? request.getStatus().toUpperCase() : "ACTIVE")
                .isActive(request.getStatus() == null || "ACTIVE".equalsIgnoreCase(request.getStatus()))
                .isDeleted(false)
                .build();

        Template savedTemplate = templateRepository.save(template);

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
            throw new com.example.cns.exception.InvalidOperationException("Cannot edit a DELETED template");
        }

        boolean contentChanged = false;

        if (request.getName() != null) {
            if (templateRepository.existsByAppIdAndName(template.getApp().getId(), request.getName())
                    && !template.getName().equals(request.getName())) {
                throw new com.example.cns.exception.DuplicateResourceException(
                        "Template with name '" + request.getName() + "' already exists.");
            }
            template.setName(request.getName());
        }
        if (request.getSubject() != null) {
            template.setSubject(request.getSubject());
        }
        if (request.getHtmlBody() != null) {
            if (request.getHtmlBody().trim().isEmpty()) {
                throw new IllegalArgumentException("HTML Body cannot be empty if provided");
            }
            template.setHtmlBody(request.getHtmlBody());
            contentChanged = true;
        }
        if (request.getStatus() != null) {
            String newStatus = request.getStatus().toUpperCase();
            template.setStatus(newStatus);
            template.setActive("ACTIVE".equalsIgnoreCase(newStatus));
        }

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
     * 3. ARCHIVE TEMPLATE
     */
    public void archiveTemplate(Long id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        if (template.isDeleted()) {
            throw new com.example.cns.exception.InvalidOperationException("Cannot archive a DELETED template");
        }

        template.setStatus("ARCHIVED");
        template.setActive(false);
        templateRepository.save(template);
        log.info("Template ID: {} moved to ARCHIVED status", id);
    }

    /**
     * 4. DELETE TEMPLATE (Soft Delete)
     */
    public void deleteTemplate(Long id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        template.setDeleted(true);
        template.setActive(false);
        template.setStatus("DELETED");
        templateRepository.save(template);
        log.info("Template ID: {} soft deleted", id);
    }

    /**
     * 4b. ACTIVATE TEMPLATE
     */
    public void activateTemplate(Long id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        if (template.isDeleted()) {
            throw new com.example.cns.exception.InvalidOperationException("Cannot activate a DELETED template");
        }

        template.setStatus("ACTIVE");
        template.setActive(true);
        templateRepository.save(template);
        log.info("Template ID: {} activated (Unarchived/Undrafted)", id);
    }

    /**
     * 5. FILTER / SEARCH (With Pagination)
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TemplateResponseDto> getTemplates(Long appId, String status,
            org.springframework.data.domain.Pageable pageable) {

        // Ensure we always filter out DELETED templates unless status="DELETED" is
        // explicitly asked?
        // User said: "the deleted ones shouldnt show if we get all templates."
        // So we strictly exclude DELETED from results.

        org.springframework.data.domain.Page<Template> templates = templateRepository.findByAppIdAndStatus(appId,
                status != null ? status.toUpperCase() : null, pageable);

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
        // Regex to find anything inside {{ }}
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
                .appName(t.getApp().getName())
                .name(t.getName())
                .htmlBody(t.getHtmlBody())
                .subject(t.getSubject())
                .status(t.getStatus())
                .detectedTags(tags) // Returns the list of tags to the frontend/user
                .build();
    }
}