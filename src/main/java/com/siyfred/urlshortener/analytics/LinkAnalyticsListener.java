package com.siyfred.urlshortener.analytics;

import com.siyfred.urlshortener.config.RabbitMQConfig;
import com.siyfred.urlshortener.repository.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class LinkAnalyticsListener {
    private static final Logger log = LoggerFactory.getLogger(LinkAnalyticsListener.class);
    private final LinkRepository linkRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String CLICK_LOCK_PREFIX = "lock:click:";
    private static final Duration CLICK_LOCK_TTL = Duration.ofSeconds(5); // creio que ninguem vai, propositalmente, acessar o mesmo link mais de uma vez em 5 segundos

    public LinkAnalyticsListener(LinkRepository linkRepository, StringRedisTemplate redisTemplate) {
        this.linkRepository = linkRepository;
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.CLICKS_QUEUE_NAME)
    public void handleLinkClick(Map<String, String> message) {
        String shortCode = message.get("shortCode");
        String ipAddress = message.get("ipAddress");
        String userAgent = message.get("userAgent");

        if (shortCode == null || ipAddress == null) {
            log.warn("Mensagem de clique inválida.");
            return;
        }

        String lockKey = CLICK_LOCK_PREFIX + shortCode + ":" + ipAddress + ":" + userAgent;

        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", CLICK_LOCK_TTL);

        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.warn("Lock já existe para [{}]. Clique duplicado ignorado.", lockKey);
            return;
        }
        log.info("Lock adquirido para [{}]. Incrementando clique.", lockKey);
        linkRepository.incrementClickCount(shortCode);
    }
}
