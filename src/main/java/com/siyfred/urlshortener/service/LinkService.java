package com.siyfred.urlshortener.service;

import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import com.siyfred.urlshortener.util.Base62;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LinkService {
    private final LinkRepository linkRepository;
    private final Base62 base62;

    public LinkService(LinkRepository linkRepository, Base62 base62) {
        this.linkRepository = linkRepository;
        this.base62 = base62;
    }

    public Optional<Link> getLongUrlByShortCode(String shortCode) {
        return linkRepository.findByShortCode(shortCode);
    }

    @Transactional
    public Link createShortUrl(String longUrl) {
        String formattedUrl = formatUrl(longUrl);

        Link link = new Link(formattedUrl, null);
        Link savedLink = linkRepository.save(link);

        long id = savedLink.getId();
        String shortCode = base62.encode(id);
        savedLink.setShortCode(shortCode);

        return linkRepository.save(savedLink);
    }

    private String formatUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL não pode ser vazia.");
        }
        String trimmedUrl = url.trim();
        if (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            return trimmedUrl;
        }
        return "https://" + trimmedUrl;
    }
}
