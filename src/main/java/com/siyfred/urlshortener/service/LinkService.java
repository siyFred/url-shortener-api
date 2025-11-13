package com.siyfred.urlshortener.service;

import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import com.siyfred.urlshortener.util.Base62;
import com.siyfred.urlshortener.util.UrlFormatter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LinkService {
    private final LinkRepository linkRepository;
    private final Base62 base62;
    private final UrlFormatter urlFormatter;
    private static final long OBFUSCATION_PRIME = 1181783497276652981L;

    public LinkService(LinkRepository linkRepository, Base62 base62, UrlFormatter urlFormatter) {
        this.linkRepository = linkRepository;
        this.base62 = base62;
        this.urlFormatter = urlFormatter;
    }

    @Cacheable(value = "links", key = "#shortCode", unless = "#result == null")
    public Optional<Link> getLongUrlByShortCode(String shortCode) {
        return linkRepository.findByShortCode(shortCode);
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

        return linkRepository.save(savedLink);
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

    @CacheEvict(value = "links", key = "#shortCode")
    public void evictCacheForShortCode(String shortCode) {
        // anotação @CacheEvict faz tudo
    }
}
