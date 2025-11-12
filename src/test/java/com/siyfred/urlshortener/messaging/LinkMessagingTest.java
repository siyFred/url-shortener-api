package com.siyfred.urlshortener.messaging;

import com.siyfred.urlshortener.BaseIT;
import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import com.siyfred.urlshortener.service.LinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
public class LinkMessagingTest extends BaseIT {
    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management-alpine")).withExposedPorts(5672);

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getFirstMappedPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LinkRepository linkRepository;

    @Autowired
    LinkService linkService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    Queue queue;

    @BeforeEach
    void clean() {
        linkRepository.deleteAll();
    }

    @Test
    void redirectToLongUrl_shouldPublishMessage_onQueue() throws Exception {
        Link saved = linkService.createShortUrl("https://google.com");
        String shortCode = saved.getShortCode();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound());

        Message msg = rabbitTemplate.receive(queue.getName(), 5000);
        assertThat(msg).isNotNull();
    }
}
