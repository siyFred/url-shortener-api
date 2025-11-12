package com.siyfred.urlshortener.analytics;

import com.siyfred.urlshortener.BaseIT;
import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import com.siyfred.urlshortener.service.LinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        "spring.rabbitmq.listener.simple.auto-startup=true" // listener ativado nesse
})
public class LinkAnalyticsListenerTest extends BaseIT {
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

    @BeforeEach
    void clean() {
        linkRepository.deleteAll();
    }

    @Test
    void redirectToLongUrl_shouldConsumeMessage_andIncreaseClickCount() throws Exception {
        Link saved = linkService.createShortUrl("https://github.com");
        saved.setClickCount(0L);
        linkRepository.save(saved);

        String shortCode = saved.getShortCode();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound());

        long deadline = System.currentTimeMillis() + 5000;
        long updated = 0L;
        while (System.currentTimeMillis() < deadline) {
            updated = linkRepository.findById(saved.getId())
                    .map(Link::getClickCount)
                    .orElse(0L);
            if (updated >= 1L) break;
            Thread.sleep(200);
        }
        assertThat(updated).isGreaterThanOrEqualTo(1L);
    }
}