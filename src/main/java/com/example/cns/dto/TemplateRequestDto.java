package com.example.cns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TemplateRequestDto {
    private Long appId;

    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String name;

    private String subject;

    private String htmlBody;

    @jakarta.validation.constraints.Pattern(regexp = "^(?i)(DRAFT|ACTIVE|ARCHIVED)$", message = "Status must be DRAFT, ACTIVE, or ARCHIVED")
    private String status;
}
