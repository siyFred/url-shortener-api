package com.siyfred.urlshortener.service;

import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class LinkService {
    private final LinkRepository linkRepository;

    public LinkService(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    public Optional<Link> getLongUrlByShortCode(String shortCode) {
        return linkRepository.findByShortCode(shortCode);
    }

    public Link createShortUrl(String longUrl) {
        // TODO: usar base62 para garantir unicidade com previsibilidade sem precisar consultar a db
        String formattedUrl = formatUrl(longUrl);
        String shortCode;
        do {
            shortCode = UUID.randomUUID().toString().substring(0, 7);
        } while(linkRepository.findByShortCode(shortCode).isPresent());

        Link link = new Link(formattedUrl, shortCode);
        return linkRepository.save(link);
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
