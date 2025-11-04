package com.siyfred.urlshortener.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String CLICKS_QUEUE_NAME = "links.clicks.queue";

    @Bean
    public Queue clicksQueue() {
        return QueueBuilder.durable(CLICKS_QUEUE_NAME).build();
    }
}
