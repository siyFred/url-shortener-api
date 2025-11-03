package com.siyfred.urlshortener.service;

import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class LinkServiceTest {
    @Mock
    private LinkRepository linkRepository;

    @InjectMocks
    private LinkService linkService;

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
        assertEquals(longUrl, resultado.get().getLongUrl(), "A URL longa não bate");
        verify(linkRepository, times(1)).findByShortCode(shortCode);
    }

    @Test
    void createShortUrl_shouldAddHttps_whenUrlIsRelative() {
        // ARRANGE
        String incomplete_longUrl = "google.com";
        String expected_longUrl = "https://google.com";
        when(linkRepository.findByShortCode(any(String.class))).thenReturn(Optional.empty());
        ArgumentCaptor<Link> linkArgumentCaptor = ArgumentCaptor.forClass(Link.class);

        // ACT
        linkService.createShortUrl(incomplete_longUrl);

        // ASSERT
        verify(linkRepository, times(1)).save(linkArgumentCaptor.capture());
        Link savedLink = linkArgumentCaptor.getValue();
        assertEquals(expected_longUrl, savedLink.getLongUrl(), "A URL salva não foi formatada corretamente (https://)");
    }
}
