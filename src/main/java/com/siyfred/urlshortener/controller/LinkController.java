package com.siyfred.urlshortener.controller;

import com.siyfred.urlshortener.config.RabbitMQConfig;
import com.siyfred.urlshortener.dto.ShortenRequest;
import com.siyfred.urlshortener.dto.ShortenResponse;
import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.service.LinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@RestController
public class LinkController {
    private static final Logger log = LoggerFactory.getLogger(LinkController.class);

    private final LinkService linkService;
    private final RabbitTemplate rabbitTemplate;

    public LinkController(LinkService linkService, RabbitTemplate rabbitTemplate) {
        this.linkService = linkService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("api/mvp/shorten")
    public ResponseEntity<ShortenResponse> shortenUrl(@RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        Link savedLink = linkService.createShortUrl(request.longUrl());

        String baseUrl = httpRequest.getRequestURL().toString().replace(httpRequest.getRequestURI(), "");
        String shortUrl = baseUrl + "/" + savedLink.getShortCode();

        ShortenResponse response = new ShortenResponse(shortUrl);
        return ResponseEntity.ok(response);
    }

    @PutMapping("api/mvp/shorten/{shortCode}")
    public ResponseEntity<ShortenResponse> updateLongUrl(@PathVariable String shortCode, @RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        Optional<Link> updated = linkService.updateLongUrlByShortCode(shortCode, request.longUrl());
        if (updated.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String baseUrl = httpRequest.getRequestURL().toString().replace(httpRequest.getRequestURI(), "");
        String shortUrl = baseUrl + "/" + shortCode;

        return ResponseEntity.ok(new ShortenResponse(shortUrl));
    }

    @DeleteMapping("api/mvp/shorten/{shortCode}/cache")
    public ResponseEntity<Void> evictCache(@PathVariable String shortCode) {
        linkService.evictCacheForShortCode(shortCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToLongUrl(@PathVariable String shortCode, HttpServletRequest httpRequest) {
        Optional<Link> link = linkService.getLongUrlByShortCode(shortCode);

        if(link.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if("GET".equals(httpRequest.getMethod())) {
            String ip = httpRequest.getRemoteAddr();
            if (ip == null || ip.isBlank()) {
                ip = "0.0.0.0";
            }
            String ua = httpRequest.getHeader("User-Agent");
            if (ua == null) {
                ua = "unknown";
            }
            Map<String, String> clickMessage = Map.of(
                    "shortCode", shortCode,
                    "ipAddress", ip,
                    "userAgent", ua
            );
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.CLICKS_QUEUE_NAME, clickMessage);
            } catch (AmqpException e) {
                log.warn("Falha ao enviar mensagem de clique para RabbitMQ; prosseguindo com redirect. Causa: {}", e.getMessage());
            }
        }

        return ResponseEntity
                .status(HttpStatus.FOUND) // acho q 302 é o que menos causa caching excessivo nos navegadores. EDIT: mudança de longUrl deu certo com 302 xD
                .location(URI.create(link.get().getLongUrl()))
                .build();
    }
}
