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

        Template template = Template.builder()
                .app(app)
                .name(request.getName())
                .subject(request.getSubject())
                .htmlBody(request.getHtmlBody())
                .status(request.getStatus() != null ? request.getStatus().toUpperCase() : "DRAFT")
                .build();

        Template savedTemplate = templateRepository.save(template);

        // Logic: Scan HTML for {{tags}} and save to DB
        List<String> extractedTags = extractAndSaveTags(savedTemplate, request.getHtmlBody());

        log.info("Template '{}' (ID: {}) created successfully with {} tags.",
                savedTemplate.getName(), savedTemplate.getId(), extractedTags.size());

        return mapToDto(savedTemplate, extractedTags);
    }

    /**
     * 2. UPDATE TEMPLATE
     * Handles updating name, subject, htmlBody and refreshing the tags.
     * Only these three fields can be updated. updatedAt and updatedBy are managed automatically.
     */
    @Transactional
    public TemplateResponseDto updateTemplate(Long id, TemplateRequestDto request) {
        log.debug("Entering updateTemplate for Template ID: {}", id);

        // Validation: Ensure required fields for update are present
        if (request.getName() == null || request.getName().isBlank()) {
            log.warn("Update failed: Template name is required");
            throw new IllegalArgumentException("Template name cannot be empty");
        }
        if (request.getSubject() == null || request.getSubject().isBlank()) {
            log.warn("Update failed: Template subject is required");
            throw new IllegalArgumentException("Template subject cannot be empty");
        }
        if (request.getHtmlBody() == null || request.getHtmlBody().isBlank()) {
            log.warn("Update failed: Template htmlBody is required");
            throw new IllegalArgumentException("Template htmlBody cannot be empty");
        }
        if (request.getUpdatedBy() == null || request.getUpdatedBy().isBlank()) {
            log.warn("Update failed: UpdatedBy (who is updating) is required");
            throw new IllegalArgumentException("UpdatedBy field is required for audit trail");
        }

        Template template = templateRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Update failed: Template ID {} not found", id);
                    return new ResourceNotFoundException("Template not found");
                });

        // Logic Check: Cannot edit archived templates
        if ("ARCHIVED".equalsIgnoreCase(template.getStatus())) {
            log.warn("Attempted to update ARCHIVED template ID: {}", id);
            throw new IllegalStateException("Cannot edit an ARCHIVED template");
        }

        // Only allow updating these three fields
        template.setName(request.getName());
        template.setSubject(request.getSubject());
        template.setHtmlBody(request.getHtmlBody());
        template.setUpdatedBy(request.getUpdatedBy());
        // updatedAt will be automatically set by @UpdateTimestamp annotation

        Template savedTemplate = templateRepository.save(template);

        // Logic: Clear old tags and re-scan because HTML changed
        tagRepository.deleteByTemplateId(savedTemplate.getId());
        List<String> tags = extractAndSaveTags(savedTemplate, request.getHtmlBody());

        log.info("Template ID: {} updated successfully by: {}", id, request.getUpdatedBy());
        return mapToDto(savedTemplate, tags);
    }

    /**
     * 3. ARCHIVE TEMPLATE
     * Logical deletion/status change.
     */
    public void archiveTemplate(Long id) {
        log.debug("Archiving template ID: {}", id);

        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        template.setStatus("ARCHIVED");
        templateRepository.save(template);

        log.info("Template ID: {} moved to ARCHIVED status", id);
    }

    /**
     * 4. DELETE TEMPLATE
     * Hard delete from database.
     */
    public void deleteTemplate(Long id) {
        log.debug("Attempting hard delete for template ID: {}", id);

        if (!templateRepository.existsById(id)) {
            log.error("Delete failed: Template ID {} does not exist", id);
            throw new ResourceNotFoundException("Cannot delete: Template not found");
        }

        templateRepository.deleteById(id);
        log.info("Template ID: {} deleted from database", id);
    }

    /**
     * 5. FILTER / SEARCH
     * Now correctly fetches tags for the response DTO.
     */
    @Transactional(readOnly = true)
    public List<TemplateResponseDto> getTemplatesByAppAndStatus(Long appId, String status) {
        log.debug("Filtering templates for App: {} with Status: {}", appId, status);

        List<Template> templates = templateRepository.findByAppIdAndStatus(appId, status.toUpperCase());

        return templates.stream().map(t -> {
            // Fetch tags for this specific template to complete the DTO
            List<String> tags = tagRepository.findByTemplateId(t.getId())
                    .stream()
                    .map(TemplateTag::getTagName)
                    .collect(Collectors.toList());
            return mapToDto(t, tags);
        }).toList();
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