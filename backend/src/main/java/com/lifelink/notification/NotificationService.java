package com.lifelink.notification;

import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Persists in-app notifications. This is also the seam where the RabbitMQ
 * publish (notify.email / notify.sms / notify.push, spec §8) plugs in later.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void notify(Long userId, NotificationType type, String title, String body) {
        User user = userRepository.getReferenceById(userId);
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notificationRepository.save(notification);
    }
}
