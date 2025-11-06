package com.siyfred.urlshortener.analytics;

import com.siyfred.urlshortener.config.RabbitMQConfig;
import com.siyfred.urlshortener.repository.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LinkAnalyticsListener {
    private static final Logger log = LoggerFactory.getLogger(LinkAnalyticsListener.class);
    private final LinkRepository linkRepository;

    public LinkAnalyticsListener(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.CLICKS_QUEUE_NAME)
    public void handleLinkClick(String shortCode) {
        log.info("Mensagem de clique recebida [shortCode={}]", shortCode);
        linkRepository.incrementClickCount(shortCode);
    }
}
