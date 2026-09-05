package com.lifelink.messaging;

import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Delivers notifications by email (spec §8). With no SMTP credentials set it
 * logs instead of sending, so a fresh checkout runs without a Mailtrap account.
 *
 * <p>A {@link MailException} is left to propagate: the listener's retry and
 * backoff take over, and a message that exhausts its attempts is dead-lettered.
 */
@Component
@Slf4j
public class EmailWorker {

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean smtpConfigured;

    public EmailWorker(
            UserRepository userRepository,
            JavaMailSender mailSender,
            @Value("${app.notifications.from}") String fromAddress,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpConfigured = smtpUsername != null && !smtpUsername.isBlank();
    }

    @RabbitListener(queues = RabbitConfig.EMAIL_QUEUE)
    public void send(NotificationMessage message) {
        User user = userRepository.findById(message.userId()).orElse(null);
        if (user == null) {
            log.warn("Dropping notification {}: user {} no longer exists",
                    message.notificationId(), message.userId());
            return;
        }

        if (!smtpConfigured) {
            log.info("Email (not sent, no SMTP credentials) to {}: {}", user.getEmail(), message.title());
            return;
        }

        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(fromAddress);
        email.setTo(user.getEmail());
        email.setSubject(message.title());
        email.setText(message.body());
        mailSender.send(email);

        log.debug("Emailed notification {} to {}", message.notificationId(), user.getEmail());
    }
}
