package com.example.cns.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class NotificationRequestDto {
    @jakarta.validation.constraints.NotBlank(message = "API Key is mandatory")
    private String apiKey;

    @NotNull(message = "Template ID is mandatory")
    private Long templateId;

    private String recipient; // For single user
    private List<String> recipients; // For multiple users

    private Map<String, Object> placeholders;
}