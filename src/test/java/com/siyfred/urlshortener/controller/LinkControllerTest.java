package com.siyfred.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siyfred.urlshortener.dto.ShortenRequest;
import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import com.siyfred.urlshortener.service.LinkService;
import com.siyfred.urlshortener.util.Base62;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
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
        registry.add("app.cache.negative-ttl", () -> "2s");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private LinkRepository linkRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Base62 base62;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        linkRepository.deleteAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void contextLoads() {
        assertThat(linkRepository).isNotNull();
        assertThat(mockMvc).isNotNull();
    }

    @Test
    @WithMockUser
    void createShortUrl_shouldCreateLink_whenUrlIsValid() throws Exception {
        // ARRANGE
        ShortenRequest request = new ShortenRequest("https://google.com");
        String requestJson = objectMapper.writeValueAsString(request);

        // ACT
        mockMvc.perform(post("/api/shorten")
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

    @Test
    void redirectToLongUrl_concurrentRequests_shouldHitDatabaseOnce() throws Exception {
        // ARRANGE
        Link saved = linkRepository.save(new Link("https://example.com/stampede", "stampede1"));
        doAnswer(inv -> {
            Thread.sleep(200);
            return Optional.of(saved);
        }).when(linkRepository).findByShortCode("stampede1");

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        // ACT
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                var response = mockMvc.perform(get("/stampede1")).andReturn().getResponse();
                return response.getStatus() + " " + response.getHeader("Location");
            }));
        }
        ready.await();
        start.countDown();

        // ASSERT
        for (Future<String> r : results) {
            assertThat(r.get(30, TimeUnit.SECONDS)).isEqualTo("302 https://example.com/stampede");
        }
        pool.shutdown();
        verify(linkRepository, times(1)).findByShortCode("stampede1");
    }

    @Test
    void redirectToLongUrl_unknownCode_shouldUseNegativeCache_andQueryAgainAfterTtlExpires() throws Exception {
        // ACT 1 & 2: duas chamadas sequenciais a um código inexistente -> apenas 1 consulta ao banco
        mockMvc.perform(get("/nao-existe-neg")).andExpect(status().isNotFound());
        mockMvc.perform(get("/nao-existe-neg")).andExpect(status().isNotFound());
        verify(linkRepository, times(1)).findByShortCode("nao-existe-neg");

        // confirma que o TTL negativo no Redis é curto (~2s, via app.cache.negative-ttl) e separado do TTL de 7 dias
        Long ttlSeconds = redisTemplate.getExpire(LinkService.LINKS_MISSING_CACHE + "::nao-existe-neg", TimeUnit.SECONDS);
        assertThat(ttlSeconds).isBetween(1L, 2L);

        // aguarda o TTL negativo expirar e confirma que o banco é consultado novamente
        Thread.sleep(2500);
        mockMvc.perform(get("/nao-existe-neg")).andExpect(status().isNotFound());
        verify(linkRepository, times(2)).findByShortCode("nao-existe-neg");
    }

    @Test
    @WithMockUser
    void createShortUrl_shouldInvalidateNegativeCache_forCreatedCode() throws Exception {
        // descobre o próximo ID da sequência do Postgres para prever o shortCode que será gerado
        Link probe = linkRepository.save(new Link("https://probe.example", "probe"));
        long nextId = probe.getId() + 1;
        linkRepository.deleteAll();
        String futureCode = base62.encode(nextId * 1181783497276652981L);

        // 1) consulta o código antes de existir -> 404 e fica em cache negativo
        mockMvc.perform(get("/" + futureCode)).andExpect(status().isNotFound());
        assertThat(cacheManager.getCache(LinkService.LINKS_MISSING_CACHE).get(futureCode)).isNotNull();

        // 2) cria o link correspondente
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShortenRequest("https://google.com"))))
                .andExpect(status().isOk());

        // 3) consulta seguinte retorna o link (302) em vez de 404
        mockMvc.perform(get("/" + futureCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://google.com"));
    }
}
