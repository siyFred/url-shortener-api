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
    private static final long OBFUSCATION_PRIME = 1181783497276652981L;

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
    void createShortUrl_shouldUseObfuscatedId_andFormatUrl() {
        // ARRANGE
        String incomplete_longUrl = "google.com";
        String expected_longUrl = "https://google.com";
        String expected_shortCode = "gE";
        long expected_id = 1000L;
        long expected_obfuscated_id = expected_id * OBFUSCATION_PRIME;

        Link linkWithId = new Link(expected_longUrl, null);
        linkWithId.setId(expected_id);

        Link finalLink = new Link(expected_longUrl, expected_shortCode);
        finalLink.setId(expected_id);

        when(linkRepository.save(any(Link.class)))
                .thenReturn(linkWithId)
                .thenReturn(finalLink);

        when(base62.encode(expected_obfuscated_id)).thenReturn(expected_shortCode);

        // ACT
        Link finalResult = linkService.createShortUrl(incomplete_longUrl);

        // ASSERT
        verify(base62, times(1)).encode(expected_obfuscated_id);
        verify(linkRepository, times(2)).save(any(Link.class));
        assertEquals(expected_longUrl, finalResult.getLongUrl(), "A URL longa não foi formatada corretamente");
        assertEquals(expected_shortCode, finalResult.getShortCode(), "O shortCode final está incorreto");
    }
}
