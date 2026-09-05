package com.lifelink.notification;

import com.lifelink.common.ForbiddenException;
import com.lifelink.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public record NotificationResponse(
            Long id, NotificationType type, String title, String body, boolean read, Instant createdAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.isRead(),
                    n.getCreatedAt());
        }
    }

    @GetMapping
    public List<NotificationResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "false") boolean unread) {
        List<Notification> notifications = unread
                ? notificationRepository.findTop50ByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public NotificationResponse markRead(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Notification", id));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not your notification");
        }
        notification.setRead(true);
        return NotificationResponse.from(notification);
    }
}
