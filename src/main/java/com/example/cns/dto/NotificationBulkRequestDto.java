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
    private List<BulkRecipient> recipients; // List of recipients for bulk sending

    private Map<String, String> globalPlaceholders; // Shared tags

    @Data
    public static class BulkRecipient {
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        @jakarta.validation.constraints.NotBlank(message = "Email is mandatory")
        private String email;

        private Map<String, String> placeholders;
    }
}
