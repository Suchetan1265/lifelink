package com.lifelink.messaging;

import com.lifelink.notification.NotificationType;

import java.time.Instant;

/**
 * What travels on the notification queues. It carries the user id rather than
 * an address so workers resolve current contact details at delivery time, and
 * the notification id so a dead-lettered message can be traced to its row.
 */
public record NotificationMessage(
        Long notificationId,
        Long userId,
        NotificationType type,
        String title,
        String body,
        Instant queuedAt) {
}
