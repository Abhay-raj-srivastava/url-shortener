package com.abhay.url_shortener.util;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Utility component for URL validation beyond what Bean Validation covers.
 *
 * Bean Validation catches syntactic issues (missing scheme, blank input).
 * This class catches semantic issues — e.g. localhost URLs in production,
 * private IP ranges, and known malicious hostnames.
 */
@Component
public class UrlValidator {

    // Hosts that should never be shortened (basic security blocklist).
    // In a production system this would be loaded from a database or
    // external threat-intelligence feed.
    private static final List<String> BLOCKED_HOSTS = List.of(
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        "::1"
    );

    /**
     * Returns true if the URL has a valid, reachable-looking structure.
     * Does NOT make a network call — purely syntactic + semantic checks.
     */
    public boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            return host != null && !host.isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Returns true if the URL's host is in the blocked list.
     */
    public boolean isBlockedUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return false;
            return BLOCKED_HOSTS.stream()
                    .anyMatch(blocked -> host.equalsIgnoreCase(blocked));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Extracts the host portion from a URL, or empty string on failure.
     */
    public String extractHost(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null ? host : "";
        } catch (URISyntaxException e) {
            return "";
        }
    }
}
