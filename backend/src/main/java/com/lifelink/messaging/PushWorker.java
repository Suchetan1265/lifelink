package com.lifelink.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Stub push channel (spec §8). The in-app notification row is what the bell
 * icon reads; this is where a device-token push would go.
 */
@Component
@Slf4j
public class PushWorker {

    @RabbitListener(queues = RabbitConfig.PUSH_QUEUE)
    public void send(NotificationMessage message) {
        log.debug("Push for user {}: {}", message.userId(), message.title());
    }
}
