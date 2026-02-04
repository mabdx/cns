package com.example.cns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TemplateRequestDto {
    @NotNull(message = "App ID is required")
    private Long appId;

    @NotBlank(message = "Template name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String name;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "HTML Body is required")
    private String htmlBody;

    private String status;

    private String updatedBy; // Name/username of who updated the template
}