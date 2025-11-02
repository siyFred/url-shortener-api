package com.siyfred.urlshortener.controller;

import com.siyfred.urlshortener.dto.ShortenRequest;
import com.siyfred.urlshortener.dto.ShortenResponse;
import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
public class LinkController {
    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping("api/mvp/shorten")
    public ResponseEntity<ShortenResponse> shortenUrl(@RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        Link savedLink = linkService.createShortUrl(request.longUrl());

        String baseUrl = httpRequest.getRequestURL().toString().replace(httpRequest.getRequestURI(), "");
        String shortUrl = baseUrl + "/" + savedLink.getShortCode();

        ShortenResponse response = new ShortenResponse(shortUrl);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToLongUrl(@PathVariable String shortCode) {
        Optional<Link> link = linkService.getLongUrlByShortCode(shortCode);

        if(link.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity
                .status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(link.get().getLongUrl()))
                .build();
    }
}
