package com.example.cns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class NotificationBulkRequestDto {
    @NotBlank
    private String apiKey;

    @NotNull
    private Long templateId;

    @NotNull
    private List<String> recipients;  // Multiple recipients

    private Map<String, String> placeholders;
}
