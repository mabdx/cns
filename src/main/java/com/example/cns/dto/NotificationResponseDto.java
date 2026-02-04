package com.example.cns.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDto {
    private Long id;
    private Long templateId;
    private String recipientEmail;
    private String subject;
    private String body;
    private String status;
    private int retryCount;
    private LocalDateTime createdAt;
    private String createdBy;
}
