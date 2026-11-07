package com.siyfred.urlshortener.service;

import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import com.siyfred.urlshortener.util.Base62;
import com.siyfred.urlshortener.util.UrlFormatter;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LinkService {
    private final LinkRepository linkRepository;
    private final Base62 base62;
    private final UrlFormatter urlFormatter;
    private static final long OBFUSCATION_PRIME = 1181783497276652981L;

    public static final String LINKS_CACHE = "links";
    public static final String LINKS_MISSING_CACHE = "links-missing";

    private final Cache linksCache;
    private final Cache missingCache;

    // Single-flight por shortCode: SINCRONIZAÇÃO LOCAL À JVM. Vale para uma única instância da API;
    // com múltiplas instâncias seria necessário um lock distribuído.
    // (Cache#get(key, Callable) não serve: o RedisCache padrão usa writer sem lock e não serializa o loader.)
    private final ConcurrentHashMap<String, CompletableFuture<Optional<Link>>> inFlight = new ConcurrentHashMap<>();

    public LinkService(LinkRepository linkRepository, Base62 base62, UrlFormatter urlFormatter,
                       CacheManager cacheManager) {
        this.linkRepository = linkRepository;
        this.base62 = base62;
        this.urlFormatter = urlFormatter;
        this.linksCache = Objects.requireNonNull(cacheManager.getCache(LINKS_CACHE));
        this.missingCache = Objects.requireNonNull(cacheManager.getCache(LINKS_MISSING_CACHE));
    }

    public Optional<Link> getLongUrlByShortCode(String shortCode) {
        Optional<Link> cached = readCaches(shortCode);
        if (cached != null) {
            return cached;
        }

        CompletableFuture<Optional<Link>> mine = new CompletableFuture<>();
        CompletableFuture<Optional<Link>> leader = inFlight.putIfAbsent(shortCode, mine);
        if (leader != null) {
            // outra thread já está consultando este shortCode: aguarda e reutiliza o resultado
            try {
                return leader.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw e;
            }
        }

        try {
            // re-checa o cache: o líder anterior pode ter terminado entre a leitura acima e o putIfAbsent
            Optional<Link> result = readCaches(shortCode);
            if (result == null) {
                result = loadAndCache(shortCode);
            }
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(shortCode, mine);
        }
    }

    /** @return o link, Optional.empty() se há cache negativo, ou null em cache miss. */
    private Optional<Link> readCaches(String shortCode) {
        Link link = linksCache.get(shortCode, Link.class);
        if (link != null) {
            return Optional.of(link);
        }
        if (missingCache.get(shortCode) != null) {
            return Optional.empty();
        }
        return null;
    }

    private Optional<Link> loadAndCache(String shortCode) {
        Optional<Link> found = linkRepository.findByShortCode(shortCode);
        if (found.isPresent()) {
            linksCache.put(shortCode, found.get());
        } else {
            missingCache.put(shortCode, Boolean.TRUE); // TTL curto (app.cache.negative-ttl)
        }
        return found;
    }

    @Transactional
    public Link createShortUrl(String longUrl) {
        String formattedUrl = urlFormatter.formatUrl(longUrl);

        Link link = new Link(formattedUrl, null);
        Link savedLink = linkRepository.save(link);

        long id = savedLink.getId();
        long obfuscatedId = (id * OBFUSCATION_PRIME);
        String shortCode = base62.encode(obfuscatedId);
        savedLink.setShortCode(shortCode);

        Link result = linkRepository.save(savedLink);
        evictMissingAfterCommit(shortCode);
        return result;
    }

    @Transactional
    @CacheEvict(value = "links", key = "#shortCode")
    public Optional<Link> updateLongUrlByShortCode(String shortCode, String newLongUrl) {
        Optional<Link> existing = linkRepository.findByShortCode(shortCode);
        if (existing.isEmpty()) return Optional.empty();

        Link link = existing.get();
        link.setLongUrl(urlFormatter.formatUrl(newLongUrl));
        Link saved = linkRepository.save(link);
        return Optional.of(saved);
    }

    public void evictCacheForShortCode(String shortCode) {
        linksCache.evict(shortCode);
        missingCache.evict(shortCode);
    }

    /** O shortCode só existe após INSERT + UPDATE; remove o cache negativo somente depois do commit. */
    private void evictMissingAfterCommit(String shortCode) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    missingCache.evict(shortCode);
                }
            });
        } else {
            missingCache.evict(shortCode);
        }
    }
}
