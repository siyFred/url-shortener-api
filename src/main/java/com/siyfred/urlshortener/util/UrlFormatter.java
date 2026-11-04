package com.siyfred.urlshortener.util;

import com.siyfred.urlshortener.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class UrlFormatter {
    private static final int MAX_URL_LENGTH = 2048;
    private static final Pattern URL_PARTS = Pattern.compile("^(https?)://([^/?#]*)(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})(\\.\\d{1,3}){3}$");
    private static final Pattern LABEL = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern TLD = Pattern.compile("^([a-z]{2,63}|xn--[a-z0-9-]{1,59})$");

    /**
     * Formata (adiciona https:// se faltar o esquema) e valida a URL.
     * Hosts internacionais são convertidos para punycode.
     *
     * @throws InvalidUrlException se a URL não for válida
     */
    public String formatUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("URL não pode ser vazia.");
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL excede o tamanho máximo permitido.");
        }

        String candidate;
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            candidate = trimmed;
        } else if (trimmed.contains("://")) {
            throw new InvalidUrlException("Apenas URLs http e https são permitidas.");
        } else {
            candidate = "https://" + trimmed;
        }

        Matcher m = URL_PARTS.matcher(candidate);
        if (!m.matches()) {
            throw invalid(trimmed);
        }
        String scheme = m.group(1).toLowerCase();
        String authority = m.group(2);
        String rest = m.group(3);

        String userInfo = "";
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            userInfo = authority.substring(0, at + 1);
            authority = authority.substring(at + 1);
        }

        String host = authority;
        String port = null;
        if (authority.startsWith("[")) {
            int end = authority.indexOf(']');
            if (end < 0) throw invalid(trimmed);
            host = authority.substring(0, end + 1);
            String after = authority.substring(end + 1);
            if (!after.isEmpty()) {
                if (!after.startsWith(":")) throw invalid(trimmed);
                port = after.substring(1);
            }
        } else {
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                host = authority.substring(0, colon);
                port = authority.substring(colon + 1);
            }
        }

        if (port != null && !port.isEmpty()) {
            if (!port.matches("\\d{1,5}") || Integer.parseInt(port) > 65535 || Integer.parseInt(port) < 1) {
                throw new InvalidUrlException("URL inválida: porta fora do intervalo em " + trimmed);
            }
        } else if (port != null) {
            throw invalid(trimmed);
        }

        if (host.isEmpty()) {
            throw new InvalidUrlException("URL inválida: host ausente em " + trimmed);
        }

        String asciiHost = validateHost(host, trimmed);

        String formatted = scheme + "://" + userInfo + asciiHost
                + (port != null ? ":" + port : "") + rest;
        try {
            new URI(formatted);
        } catch (URISyntaxException e) {
            throw invalid(trimmed);
        }
        return formatted;
    }

    private InvalidUrlException invalid(String original) {
        return new InvalidUrlException("URL inválida: " + original);
    }

    /** Valida o host e devolve sua forma ASCII (punycode). Bloqueia loopback e redes privadas. */
    private String validateHost(String host, String original) {
        if (host.startsWith("[")) {
            checkAddress(host.substring(1, host.length() - 1), original);
            return host;
        }
        if (IPV4.matcher(host).matches()) {
            for (String part : host.split("\\.")) {
                if (Integer.parseInt(part) > 255) throw invalid(original);
            }
            checkAddress(host, original);
            return host;
        }

        String ascii;
        try {
            ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase();
        } catch (IllegalArgumentException e) {
            throw invalidDomain(original);
        }
        if (ascii.endsWith(".")) {
            ascii = ascii.substring(0, ascii.length() - 1);
        }
        if (ascii.isEmpty() || ascii.length() > 253) throw invalidDomain(original);

        String[] labels = ascii.split("\\.", -1);
        if (labels.length < 2) throw invalidDomain(original); // "google" sem TLD / "localhost"
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) throw invalidDomain(original);
        }
        if (!TLD.matcher(labels[labels.length - 1]).matches()) throw invalidDomain(original);
        return ascii;
    }

    private InvalidUrlException invalidDomain(String original) {
        return new InvalidUrlException("URL inválida: domínio inexistente ou malformado em " + original);
    }

    private void checkAddress(String literal, String original) {
        InetAddress addr;
        try {
            addr = InetAddress.getByName(literal); // literal IP: sem consulta DNS
        } catch (UnknownHostException e) {
            throw invalid(original);
        }
        byte[] b = addr.getAddress();
        boolean uniqueLocalV6 = b.length == 16 && (b[0] & 0xFE) == 0xFC;
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isSiteLocalAddress()
                || addr.isLinkLocalAddress() || uniqueLocalV6) {
            throw new InvalidUrlException("URL inválida: endereços locais ou de rede privada não são permitidos.");
        }
    }
}
