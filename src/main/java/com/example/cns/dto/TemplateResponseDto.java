package com.example.cns.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TemplateResponseDto {
    private Long id;
    private Long appId;
    private String appName;
    private String name;
    private String subject;
    private String htmlBody;
    private String status;
    private List<TagInfo> detectedTags;

    @Data
    @Builder
    public static class TagInfo {
        private String tagName;
        private String datatype;
    }

    private Boolean isDeleted;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}