package com.example.cns.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TemplateResponseDto {
    private Long id;
    private String appName;
    private String name;
    private String subject;
    private String htmlBody;
    private String status;
    private List<String> detectedTags;
    private String createdBy;
    private String updatedBy;
}