package com.example.fichestu.api;

import com.example.fichestu.api.GameDtos.GenericMessageResponse;
import com.example.fichestu.api.NotificationDtos.NotificationListResponse;
import com.example.fichestu.service.NotificationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationListResponse list() {
        return notificationService.listCurrentUserNotifications();
    }

    @PostMapping("/{notificationId}/read")
    public GenericMessageResponse markAsRead(@PathVariable Integer notificationId) {
        return notificationService.markAsRead(notificationId);
    }

    @PostMapping("/read-all")
    public GenericMessageResponse markAllAsRead() {
        return notificationService.markAllAsRead();
    }

    @DeleteMapping
    public GenericMessageResponse clearAll() {
        return notificationService.clearAll();
    }
}
