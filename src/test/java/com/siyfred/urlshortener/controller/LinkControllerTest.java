package com.siyfred.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siyfred.urlshortener.dto.ShortenRequest;
import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import com.siyfred.urlshortener.util.Base62;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
public class LinkControllerTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        // não inicia listeners Rabbit durante o teste de controller
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private LinkRepository linkRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Base62 base62;

    @BeforeEach
    void setUp() {
        linkRepository.deleteAll();
    }

    @Test
    void contextLoads() {
        assertThat(linkRepository).isNotNull();
        assertThat(mockMvc).isNotNull();
    }

    @Test
    void createShortUrl_shouldCreateLink_whenUrlIsValid() throws Exception {
        // ARRANGE
        ShortenRequest request = new ShortenRequest("https://google.com");
        String requestJson = objectMapper.writeValueAsString(request);

        // ACT
        mockMvc.perform(post("/api/mvp/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))

                // ASSERT Http response
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortUrl").exists());

        // ASSERT Database
        List<Link> links = linkRepository.findAll();
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getLongUrl()).isEqualTo("https://google.com");

        Long savedId = links.get(0).getId();
        String saved_shortCode = links.get(0).getShortCode();
        assertThat(saved_shortCode).isEqualTo(base62.encode(savedId * 1181783497276652981L));
    }

    @Test
    void redirectToLongUrl_shouldRedirect_whenCodeExists() throws Exception {
        // ARRANGE
        Link link = new Link("https://github.com", "abc1234");
        linkRepository.save(link);

        // ACT
        mockMvc.perform(get("/" + link.getShortCode()))

                // ASSERT
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://github.com"));
    }

    @Test
    void redirectToLongUrl_shouldReturnNotFound_whenCodeDoesNotExist() throws Exception {
        // ARRANGE (nada)
        // ACT
        mockMvc.perform(get("/codigo-nao-existe"))

                // ASSERT
                .andExpect(status().isNotFound());
    }

    @Test
    void redirectToLongUrl_shouldUseCache_onSecondCall() throws Exception {
        // ARRANGE
        Link link = new Link("https://google.com", "cached-key");
        linkRepository.save(link);

        // ACT 1
        mockMvc.perform(get("/" + link.getShortCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));

        // ASSERT 1
        verify(linkRepository, times(1)).findByShortCode("cached-key");

        // ACT 2
        mockMvc.perform(get("/" + link.getShortCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));

        // ASSERT
        verify(linkRepository, times(1)).findByShortCode("cached-key");
    }
}
