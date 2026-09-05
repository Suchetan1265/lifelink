package com.lifelink.messaging;

import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Mock SMS channel (spec §8): logs what a Twilio call would send. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsWorker {

    private final UserRepository userRepository;

    @RabbitListener(queues = RabbitConfig.SMS_QUEUE)
    public void send(NotificationMessage message) {
        User user = userRepository.findById(message.userId()).orElse(null);
        if (user == null || user.getPhone() == null || user.getPhone().isBlank()) {
            log.debug("Skipping SMS for notification {}: no phone on file", message.notificationId());
            return;
        }
        log.info("SMS to {}: {} — {}", user.getPhone(), message.title(), message.body());
    }
}
