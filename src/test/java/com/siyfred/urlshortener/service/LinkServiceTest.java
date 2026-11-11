package com.siyfred.urlshortener.service;

import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import com.siyfred.urlshortener.util.Base62;
import com.siyfred.urlshortener.util.UrlFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class LinkServiceTest {
    private static final long OBFUSCATION_PRIME = 1181783497276652981L;

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private Base62 base62;

    @Mock
    private UrlFormatter urlFormatter;

    private ConcurrentMapCacheManager cacheManager;
    private LinkService linkService;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(LinkService.LINKS_CACHE, LinkService.LINKS_MISSING_CACHE);
        linkService = new LinkService(linkRepository, base62, urlFormatter, cacheManager);
    }

    @Test
    void getLongUrlByShortCode_shouldReturnLink_whenCodeExists() {
        // ARRANGE
        String shortCode = "123abc7";
        String longUrl = "https://google.com";
        Link falseLink = new Link(longUrl, shortCode);
        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(falseLink));

        // ACT
        Optional<Link> resultado = linkService.getLongUrlByShortCode(shortCode);

        // ASSERT
        assertTrue(resultado.isPresent(), "O resultado não deveria estar vazio");
        assertEquals(longUrl, resultado.get().getLongUrl(), "A URL longa recuperada do repositorio mockado esta incorreta");
        verify(linkRepository, times(1)).findByShortCode(shortCode);
    }

    @Test
    void createShortUrl_shouldUseObfuscatedId_andFormatUrl() {
        // ARRANGE
        String incomplete_longUrl = "google.com";
        String expected_longUrl = "https://google.com";
        String expected_shortCode = "gE";
        long expected_id = 1000L;
        long expected_obfuscated_id = (expected_id * OBFUSCATION_PRIME) & Long.MAX_VALUE;

        when(urlFormatter.formatUrl(incomplete_longUrl)).thenReturn(expected_longUrl);

        Link linkWithId = new Link(expected_longUrl, null);
        linkWithId.setId(expected_id);

        Link finalLink = new Link(expected_longUrl, expected_shortCode);
        finalLink.setId(expected_id);

        when(linkRepository.save(any(Link.class)))
                .thenReturn(linkWithId)
                .thenReturn(finalLink);

        when(base62.encode(expected_obfuscated_id)).thenReturn(expected_shortCode);

        // ACT
        Link finalResult = linkService.createShortUrl(incomplete_longUrl);

        // ASSERT
        verify(urlFormatter, times(1)).formatUrl(incomplete_longUrl);
        verify(base62, times(1)).encode(expected_obfuscated_id);
        verify(linkRepository, times(2)).save(any(Link.class));
        assertEquals(expected_longUrl, finalResult.getLongUrl(), "A URL longa não foi formatada corretamente");
        assertEquals(expected_shortCode, finalResult.getShortCode(), "O shortCode final está incorreto");
    }

    @Test
    void getLongUrlByShortCode_concurrentRequests_shouldHitRepositoryOnce() throws Exception {
        String shortCode = "stampede1";
        Link link = new Link("https://example.com", shortCode);
        when(linkRepository.findByShortCode(shortCode)).thenAnswer(inv -> {
            Thread.sleep(200);
            return Optional.of(link);
        });

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<Link>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return linkService.getLongUrlByShortCode(shortCode);
            }));
        }
        ready.await();
        start.countDown();

        for (Future<Optional<Link>> f : futures) {
            Optional<Link> result = f.get(10, TimeUnit.SECONDS);
            assertTrue(result.isPresent());
            assertEquals("https://example.com", result.get().getLongUrl());
        }
        pool.shutdown();

        verify(linkRepository, times(1)).findByShortCode(shortCode);
    }

    @Test
    void getLongUrlByShortCode_unknownCode_shouldBeCachedInMissingCache() {
        when(linkRepository.findByShortCode("missing1")).thenReturn(Optional.empty());

        assertTrue(linkService.getLongUrlByShortCode("missing1").isEmpty());
        assertTrue(linkService.getLongUrlByShortCode("missing1").isEmpty());

        verify(linkRepository, times(1)).findByShortCode("missing1");
        assertNotNull(cacheManager.getCache(LinkService.LINKS_MISSING_CACHE).get("missing1"));
    }

    @Test
    void createShortUrl_shouldEvictNegativeCacheAfterCommit() {
        String shortCode = "gE";
        long id = 1000L;
        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.empty());

        // 1) consulta antes da criação: popula o cache negativo
        assertTrue(linkService.getLongUrlByShortCode(shortCode).isEmpty());
        assertNotNull(cacheManager.getCache(LinkService.LINKS_MISSING_CACHE).get(shortCode));

        // 2) cria o link dentro de sincronização transacional ativa: a invalidação ocorre apenas no afterCommit
        when(urlFormatter.formatUrl("google.com")).thenReturn("https://google.com");
        Link withId = new Link("https://google.com", null);
        withId.setId(id);
        Link saved = new Link("https://google.com", shortCode);
        saved.setId(id);
        when(linkRepository.save(any(Link.class))).thenReturn(withId).thenReturn(saved);
        when(base62.encode((id * OBFUSCATION_PRIME) & Long.MAX_VALUE)).thenReturn(shortCode);

        TransactionSynchronizationManager.initSynchronization();
        try {
            linkService.createShortUrl("google.com");
            // antes do commit, a entrada negativa ainda existe
            assertNotNull(cacheManager.getCache(LinkService.LINKS_MISSING_CACHE).get(shortCode));
            // simula o commit da transação
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertNull(cacheManager.getCache(LinkService.LINKS_MISSING_CACHE).get(shortCode));

        // 3) consulta seguinte vai ao banco e retorna o link criado
        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(saved));
        Optional<Link> afterCreate = linkService.getLongUrlByShortCode(shortCode);
        assertTrue(afterCreate.isPresent());
        assertEquals("https://google.com", afterCreate.get().getLongUrl());
    }

    @Test
    void createShortUrl_shouldGenerateValidUniqueShortCodes_whenIdMultipliedOverflows64Bits() {
        LinkService serviceWithRealBase62 = new LinkService(linkRepository, new Base62(), urlFormatter, cacheManager);
        when(urlFormatter.formatUrl(anyString())).thenReturn("https://example.com");

        // IDs 8 e 9 causavam overflow para negativo sem a máscara & Long.MAX_VALUE
        for (long id = 1L; id <= 20L; id++) {
            long currentId = id;
            when(linkRepository.save(any(Link.class))).thenAnswer(inv -> {
                Link arg = inv.getArgument(0);
                if (arg.getId() == null) {
                    arg.setId(currentId);
                }
                return arg;
            });

            Link created = serviceWithRealBase62.createShortUrl("https://example.com");
            assertNotNull(created.getShortCode());
            assertFalse(created.getShortCode().isEmpty(), "O shortCode não pode ser vazio para o ID " + id);
        }
    }
}
