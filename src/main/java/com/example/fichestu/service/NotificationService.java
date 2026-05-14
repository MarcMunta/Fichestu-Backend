package com.example.fichestu.service;

import com.example.fichestu.api.GameDtos.GenericMessageResponse;
import com.example.fichestu.api.NotificationDtos.NotificationDto;
import com.example.fichestu.api.NotificationDtos.NotificationListResponse;
import com.example.fichestu.persistence.entity.NotificationEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.NotificationRepository;
import com.example.fichestu.security.CurrentUserService;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private static final Set<String> EMAIL_UPDATE_TYPES = Set.of(
        "BALL_REVEAL",
        "BATTLE_FINISHED",
        "BATTLE_WIN",
        "MATCHMAKING_CANCELLED"
    );

    private final CurrentUserService currentUserService;
    private final NotificationRepository notificationRepository;
    private final AutomatedEmailService automatedEmailService;

    public NotificationService(
        CurrentUserService currentUserService,
        NotificationRepository notificationRepository,
        AutomatedEmailService automatedEmailService
    ) {
        this.currentUserService = currentUserService;
        this.notificationRepository = notificationRepository;
        this.automatedEmailService = automatedEmailService;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse listCurrentUserNotifications() {
        UserEntity user = currentUserService.requireUserEntity();
        return listForUser(user.getUserId());
    }

    @Transactional
    public GenericMessageResponse markAsRead(Integer notificationId) {
        UserEntity user = currentUserService.requireUserEntity();
        NotificationEntity notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificacion no encontrada"));
        if (!notification.getUser().getUserId().equals(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes leer esta notificacion");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return new GenericMessageResponse("Notificacion leida", true);
    }

    @Transactional
    public GenericMessageResponse markAllAsRead() {
        UserEntity user = currentUserService.requireUserEntity();
        Instant now = Instant.now();
        notificationRepository.findTop20ByUser_UserIdOrderByCreatedAtDesc(user.getUserId()).stream()
            .filter(notification -> notification.getReadAt() == null)
            .forEach(notification -> notification.setReadAt(now));
        return new GenericMessageResponse("Notificaciones leidas", true);
    }

    @Transactional
    public GenericMessageResponse clearAll() {
        UserEntity user = currentUserService.requireUserEntity();
        notificationRepository.deleteByUser_UserId(user.getUserId());
        return new GenericMessageResponse("Notificaciones eliminadas", true);
    }

    @Transactional
    public void create(UserEntity user, String title, String message, String type) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notificationRepository.save(notification);
        if (EMAIL_UPDATE_TYPES.contains(type)) {
            automatedEmailService.sendImportantUpdateEmail(user, title, message, type);
        }
    }

    private NotificationListResponse listForUser(Integer userId) {
        return new NotificationListResponse(
            notificationRepository.findTop20ByUser_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList(),
            notificationRepository.countByUser_UserIdAndReadAtIsNull(userId)
        );
    }

    private NotificationDto toDto(NotificationEntity notification) {
        return new NotificationDto(
            notification.getNotificationId(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getType(),
            notification.getReadAt() != null,
            notification.getCreatedAt()
        );
    }
}
