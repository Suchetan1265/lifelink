package com.lifelink.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Fans a saved notification out to the delivery channels (spec §8).
 *
 * <p>Publishing is best-effort: the in-app notification is already committed
 * to Postgres by the time this runs, so a broker outage costs an email, not
 * the notification itself.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(NotificationMessage message) {
        send(RabbitConfig.EMAIL_ROUTING_KEY, message);
        send(RabbitConfig.PUSH_ROUTING_KEY, message);
        // SMS costs money, so it is reserved for the time-critical types.
        if (message.type().warrantsSms()) {
            send(RabbitConfig.SMS_ROUTING_KEY, message);
        }
    }

    private void send(String routingKey, NotificationMessage message) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, message);
        } catch (AmqpException e) {
            log.warn("Could not queue notification {} on {}: {}",
                    message.notificationId(), routingKey, e.getMessage());
        }
    }
}
