package com.example.cns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
public class NotificationRequestDto {
    @NotBlank
    private String apiKey;

    @NotNull
    private Long templateId;

    @NotBlank
    private String recipient;

    private Map<String, String> placeholders;
}