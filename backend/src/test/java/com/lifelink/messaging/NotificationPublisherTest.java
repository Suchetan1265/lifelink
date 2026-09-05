package com.lifelink.messaging;

import com.lifelink.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Which channels a notification fans out to (spec §8). */
@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NotificationPublisher publisher;

    @Test
    void urgentTypesAlsoGoToSms() {
        publisher.publish(message(NotificationType.MATCH_FOUND));

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.EMAIL_ROUTING_KEY), any(Object.class));
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.PUSH_ROUTING_KEY), any(Object.class));
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.SMS_ROUTING_KEY), any(Object.class));
    }

    @Test
    void routineTypesSkipSms() {
        publisher.publish(message(NotificationType.VERIFICATION_RESULT));

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.EMAIL_ROUTING_KEY), any(Object.class));
        verify(rabbitTemplate, never()).convertAndSend(
                eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.SMS_ROUTING_KEY), any(Object.class));
    }

    @Test
    void aBrokerOutageDoesNotPropagate() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertThatCode(() -> publisher.publish(message(NotificationType.MATCH_FOUND)))
                .doesNotThrowAnyException();
    }

    private static NotificationMessage message(NotificationType type) {
        return new NotificationMessage(1L, 42L, type, "Title", "Body", Instant.now());
    }
}
