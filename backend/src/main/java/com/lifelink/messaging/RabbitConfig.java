package com.lifelink.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The notification fan-out from spec §8: one topic exchange, a queue per
 * channel, and a dead-letter queue behind each so a send that keeps failing
 * ends up somewhere inspectable rather than looping.
 *
 * <p>Retry and backoff are configured on the listener container in
 * application.yml; a message that exhausts its attempts is rejected, which
 * routes it to this exchange's dead-letter counterpart.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "notifications";
    public static final String DEAD_LETTER_EXCHANGE = "notifications.dlx";

    public static final String EMAIL_ROUTING_KEY = "notify.email";
    public static final String SMS_ROUTING_KEY = "notify.sms";
    public static final String PUSH_ROUTING_KEY = "notify.push";

    public static final String EMAIL_QUEUE = "notify.email";
    public static final String SMS_QUEUE = "notify.sms";
    public static final String PUSH_QUEUE = "notify.push";

    @Bean
    TopicExchange notificationsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange notificationsDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue emailQueue() {
        return channelQueue(EMAIL_QUEUE);
    }

    @Bean
    Queue smsQueue() {
        return channelQueue(SMS_QUEUE);
    }

    @Bean
    Queue pushQueue() {
        return channelQueue(PUSH_QUEUE);
    }

    @Bean
    Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterName(EMAIL_QUEUE)).build();
    }

    @Bean
    Queue smsDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterName(SMS_QUEUE)).build();
    }

    @Bean
    Queue pushDeadLetterQueue() {
        return QueueBuilder.durable(deadLetterName(PUSH_QUEUE)).build();
    }

    @Bean
    Binding emailBinding(Queue emailQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(emailQueue).to(notificationsExchange).with(EMAIL_ROUTING_KEY);
    }

    @Bean
    Binding smsBinding(Queue smsQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(smsQueue).to(notificationsExchange).with(SMS_ROUTING_KEY);
    }

    @Bean
    Binding pushBinding(Queue pushQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(pushQueue).to(notificationsExchange).with(PUSH_ROUTING_KEY);
    }

    @Bean
    Binding emailDeadLetterBinding(Queue emailDeadLetterQueue, DirectExchange notificationsDeadLetterExchange) {
        return BindingBuilder.bind(emailDeadLetterQueue)
                .to(notificationsDeadLetterExchange).with(deadLetterName(EMAIL_QUEUE));
    }

    @Bean
    Binding smsDeadLetterBinding(Queue smsDeadLetterQueue, DirectExchange notificationsDeadLetterExchange) {
        return BindingBuilder.bind(smsDeadLetterQueue)
                .to(notificationsDeadLetterExchange).with(deadLetterName(SMS_QUEUE));
    }

    @Bean
    Binding pushDeadLetterBinding(Queue pushDeadLetterQueue, DirectExchange notificationsDeadLetterExchange) {
        return BindingBuilder.bind(pushDeadLetterQueue)
                .to(notificationsDeadLetterExchange).with(deadLetterName(PUSH_QUEUE));
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    private static Queue channelQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterName(name))
                .build();
    }

    private static String deadLetterName(String queue) {
        return queue + ".dlq";
    }
}
