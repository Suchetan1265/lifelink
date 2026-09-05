package com.lifelink.notification;

import com.lifelink.messaging.NotificationMessage;
import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Persists in-app notifications and hands them to the delivery channels.
 *
 * <p>The row in Postgres is what the notification bell reads and is written
 * synchronously; email, SMS and push go out over RabbitMQ once the surrounding
 * transaction commits (spec §8).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public void notify(Long userId, NotificationType type, String title, String body) {
        User user = userRepository.getReferenceById(userId);
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notificationRepository.save(notification);

        events.publishEvent(new NotificationMessage(
                notification.getId(), userId, type, title, body, Instant.now()));
    }
}
