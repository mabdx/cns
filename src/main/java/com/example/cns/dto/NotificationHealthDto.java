package com.example.cns.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationHealthDto {
    private long successfulNotifications;
    private long failedNotifications;
    private String healthPercentage;
}