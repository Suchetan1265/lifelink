package com.lifelink.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Holds notifications back until the transaction that produced them commits,
 * so work that rolls back never sends an email about something that did not
 * happen.
 */
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationSaved(NotificationMessage message) {
        publisher.publish(message);
    }
}
