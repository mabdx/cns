package com.example.cns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class NotificationBulkRequestDto {
    @NotBlank(message = "API Key is mandatory")
    private String apiKey;

    @NotNull(message = "Template ID is mandatory")
    private Long templateId;

    @jakarta.validation.constraints.NotEmpty(message = "Recipients list cannot be empty")
    private Map<String, Map<String, String>> recipients; // Email -> Personalized Placeholders

    private Map<String, String> globalPlaceholders; // Shared tags
}
