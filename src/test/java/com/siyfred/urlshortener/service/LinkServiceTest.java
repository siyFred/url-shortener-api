package com.siyfred.urlshortener.service;

import com.siyfred.urlshortener.model.Link;
import com.siyfred.urlshortener.repository.LinkRepository;
import com.siyfred.urlshortener.util.Base62;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private Base62 base62;

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
        assertEquals(longUrl, resultado.get().getLongUrl(), "A URL longa recuperada do repositorio mockado esta incorreta");
        verify(linkRepository, times(1)).findByShortCode(shortCode);
    }

    @Test
    void createShortUrl_shouldAddHttps_whenUrlIsRelative() {
        // ARRANGE
        String incomplete_longUrl = "google.com";
        String expected_longUrl = "https://google.com";
        String expected_shortCode = "gE";

        Link linkWithoutId = new Link(expected_longUrl, null);
        Link linkWithId = new Link(expected_longUrl, null);
        linkWithId.setId(1000L);

        Link finalLink = new Link(expected_longUrl, expected_shortCode);
        finalLink.setId(1000L);

        when(linkRepository.save(any(Link.class)))
                .thenReturn(linkWithId)
                .thenReturn(finalLink);

        when(base62.encode(1000L)).thenReturn(expected_shortCode);

        // ACT
        Link finalResult = linkService.createShortUrl(incomplete_longUrl);

        // ASSERT
        verify(base62, times(1)).encode(1000L);
        verify(linkRepository, times(2)).save(any(Link.class));
        assertEquals(expected_longUrl, finalResult.getLongUrl(), "A URL longa não foi formatada corretamente");
        assertEquals(expected_shortCode, finalResult.getShortCode(), "O shortCode final está incorreto");
    }
}
