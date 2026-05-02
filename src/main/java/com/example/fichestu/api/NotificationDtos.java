package com.example.fichestu.api;

import java.time.Instant;
import java.util.List;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationDto(
        Integer id,
        String title,
        String message,
        String type,
        Boolean read,
        Instant createdAt
    ) {
    }

    public record NotificationListResponse(
        List<NotificationDto> notifications,
        Long unreadCount
    ) {
    }
}
