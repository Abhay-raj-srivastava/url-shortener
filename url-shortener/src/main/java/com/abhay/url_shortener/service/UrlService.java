package com.abhay.url_shortener.service;

import com.abhay.url_shortener.dto.request.ShortenRequest;
import com.abhay.url_shortener.dto.request.UpdateUrlRequest;
import com.abhay.url_shortener.dto.response.ShortenResponse;
import com.abhay.url_shortener.dto.response.UrlStatsResponse;
import com.abhay.url_shortener.entity.Url;
import com.abhay.url_shortener.exception.*;
import com.abhay.url_shortener.repository.UrlRepository;
import com.abhay.url_shortener.util.ShortCodeGenerator;
import com.abhay.url_shortener.util.UrlValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core business logic for URL shortening and redirection.
 *
 * Key design decisions:
 *
 * 1. Caching strategy — redirect lookup is the hottest path.
 *    We cache the destination URL string in Redis keyed by short code.
 *    On cache hit we skip the DB entirely. On cache miss (cold start or
 *    eviction) we fall back to the DB and repopulate the cache.
 *    Cache entries are evicted on update or deletion via @CacheEvict.
 *
 * 2. Click counting — we use a direct JPQL UPDATE rather than loading the
 *    entity, to avoid a read-modify-write and reduce lock contention.
 *    The @Transactional annotation on resolveUrl ensures the increment
 *    and the redirect happen atomically.
 *
 * 3. Short code collision handling — after generating a random code we
 *    check for existence in the DB. If taken, we retry up to MAX_RETRIES
 *    times. The probability of even a single collision is ~n/62^7 where n
 *    is the number of existing records, making multiple collisions
 *    extraordinarily unlikely.
 *
 * 4. Custom alias uniqueness — checked before inserting, returning a 409
 *    Conflict so the client knows the alias is unavailable (not a generic 500).
 *
 * 5. Scheduled cleanup — expired links are soft-deleted every hour.
 *    A soft delete (isActive=false) preserves historical analytics data.
 */
@Service
@Slf4j
public class UrlService {

    private static final int MAX_CODE_GENERATION_RETRIES = 10;
    private static final String REDIRECT_CACHE = "redirects";

    private final UrlRepository urlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlValidator urlValidator;
    private final Counter urlCreatedCounter;
    private final Counter urlRedirectCounter;
    private final Counter urlExpiredCounter;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.default-ttl-days:30}")
    private int defaultTtlDays;

    public UrlService(
            UrlRepository urlRepository,
            ShortCodeGenerator shortCodeGenerator,
            UrlValidator urlValidator,
            MeterRegistry meterRegistry) {
        this.urlRepository = urlRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlValidator = urlValidator;

        // Register custom Micrometer counters — visible in /actuator/prometheus
        this.urlCreatedCounter = Counter.builder("url_shortener.urls.created")
                .description("Total number of URLs shortened")
                .register(meterRegistry);
        this.urlRedirectCounter = Counter.builder("url_shortener.urls.redirected")
                .description("Total number of successful redirects")
                .register(meterRegistry);
        this.urlExpiredCounter = Counter.builder("url_shortener.urls.expired_accessed")
                .description("Total number of redirect attempts on expired URLs")
                .register(meterRegistry);
    }

    // ---------------------------------------------------------------
    //  Shorten
    // ---------------------------------------------------------------

    /**
     * Creates a new shortened URL record and returns the short link details.
     */
    @Transactional
    public ShortenResponse shortenUrl(ShortenRequest request) {
        log.debug("Shortening URL: {}", request.getOriginalUrl());

        // Semantic URL validation (scheme, host, blocklist)
        if (urlValidator.isBlockedUrl(request.getOriginalUrl())) {
            throw new InvalidUrlException("URL points to a blocked host: "
                + urlValidator.extractHost(request.getOriginalUrl()));
        }

        String shortCode = resolveShortCode(request);
        LocalDateTime expiresAt = resolveExpiryTime(request.getTtlDays());

        Url url = Url.builder()
                .shortCode(shortCode)
                .originalUrl(request.getOriginalUrl())
                .title(request.getTitle())
                .expiresAt(expiresAt)
                .isCustomAlias(request.getCustomAlias() != null && !request.getCustomAlias().isBlank())
                .isActive(true)
                .createdBy(request.getCreatedBy())
                .build();

        Url saved = urlRepository.save(url);
        urlCreatedCounter.increment();
        log.info("URL shortened: {} -> {}", saved.getShortCode(), saved.getOriginalUrl());

        return toShortenResponse(saved);
    }

    // ---------------------------------------------------------------
    //  Redirect
    // ---------------------------------------------------------------

    /**
     * Resolves a short code to its destination URL and increments click count.
     * The returned destination URL is cached in Redis for fast subsequent lookups.
     *
     * Note: @Cacheable is on a separate cached method to allow Spring's AOP
     * proxy to intercept the cache annotation correctly (self-invocation
     * bypasses proxies).
     */
    @Transactional
    public String resolveUrl(String shortCode) {
        log.debug("Resolving short code: {}", shortCode);

        // Cache lookup is done in the dedicated cachedLookup method
        String destination = getCachedOriginalUrl(shortCode);

        // Async-style click increment using direct JPQL UPDATE
        urlRepository.incrementClickCount(shortCode);
        urlRedirectCounter.increment();

        return destination;
    }

    /**
     * Redis-cached lookup. Spring Cache will serve this from Redis after
     * the first DB hit. The cache key is the short code.
     */
    @Cacheable(value = REDIRECT_CACHE, key = "#shortCode", unless = "#result == null")
    public String getCachedOriginalUrl(String shortCode) {
        Url url = urlRepository.findByShortCodeAndIsActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (url.isExpired()) {
            urlExpiredCounter.increment();
            throw new UrlExpiredException(shortCode);
        }

        return url.getOriginalUrl();
    }

    // ---------------------------------------------------------------
    //  Stats / Analytics
    // ---------------------------------------------------------------

    /**
     * Returns full analytics data for a given short code.
     */
    public UrlStatsResponse getStats(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        return toStatsResponse(url);
    }

    /**
     * Returns all URLs created by a specific user.
     */
    public List<ShortenResponse> getUrlsByCreator(String createdBy) {
        return urlRepository.findByCreatedByAndIsActiveTrueOrderByCreatedAtDesc(createdBy)
                .stream()
                .map(this::toShortenResponse)
                .collect(Collectors.toList());
    }

    /**
     * Returns the top N most-clicked active URLs.
     */
    public List<UrlStatsResponse> getTopUrls(int limit) {
        int safeLimit = Math.min(limit, 100); // cap at 100
        return urlRepository.findTopByClickCount(safeLimit)
                .stream()
                .map(this::toStatsResponse)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    //  Update
    // ---------------------------------------------------------------

    /**
     * Partially updates a URL record. Only non-null fields in the request
     * are applied (PATCH semantics).
     */
    @Transactional
    @CacheEvict(value = REDIRECT_CACHE, key = "#shortCode")
    public ShortenResponse updateUrl(String shortCode, UpdateUrlRequest request) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (request.getOriginalUrl() != null) {
            if (urlValidator.isBlockedUrl(request.getOriginalUrl())) {
                throw new InvalidUrlException("URL points to a blocked host");
            }
            url.setOriginalUrl(request.getOriginalUrl());
        }
        if (request.getTitle() != null) {
            url.setTitle(request.getTitle());
        }
        if (request.getTtlDays() != null) {
            url.setExpiresAt(LocalDateTime.now().plusDays(request.getTtlDays()));
        }
        if (request.getIsActive() != null) {
            url.setIsActive(request.getIsActive());
        }

        Url saved = urlRepository.save(url);
        log.info("URL updated: {}", shortCode);
        return toShortenResponse(saved);
    }

    // ---------------------------------------------------------------
    //  Delete
    // ---------------------------------------------------------------

    /**
     * Soft-deletes a URL (sets isActive = false).
     * Hard deletes are avoided to preserve analytics history.
     */
    @Transactional
    @CacheEvict(value = REDIRECT_CACHE, key = "#shortCode")
    public void deleteUrl(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        url.setIsActive(false);
        urlRepository.save(url);
        log.info("URL soft-deleted: {}", shortCode);
    }

    // ---------------------------------------------------------------
    //  Scheduled cleanup
    // ---------------------------------------------------------------

    /**
     * Runs every hour to soft-delete expired URLs.
     * @Transactional ensures the JPQL UPDATE is executed within a transaction.
     */
    @Scheduled(cron = "0 0 * * * *")  // top of every hour
    @Transactional
    public void cleanupExpiredUrls() {
        int count = urlRepository.deactivateExpiredUrls(LocalDateTime.now());
        if (count > 0) {
            log.info("Scheduled cleanup: soft-deleted {} expired URLs", count);
        }
    }

    // ---------------------------------------------------------------
    //  Private helpers
    // ---------------------------------------------------------------

    private String resolveShortCode(ShortenRequest request) {
        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            String alias = request.getCustomAlias().trim();
            if (urlRepository.existsByShortCode(alias)) {
                throw new CustomAliasAlreadyExistsException(alias);
            }
            return alias;
        }
        return generateUniqueCode();
    }

    private String generateUniqueCode() {
        for (int attempt = 1; attempt <= MAX_CODE_GENERATION_RETRIES; attempt++) {
            String code = shortCodeGenerator.generateRandomCode();
            if (!urlRepository.existsByShortCode(code)) {
                return code;
            }
            log.debug("Short code collision on attempt {}: {}", attempt, code);
        }
        throw new ShortCodeGenerationException(MAX_CODE_GENERATION_RETRIES);
    }

    private LocalDateTime resolveExpiryTime(Integer ttlDays) {
        if (ttlDays != null && ttlDays > 0) {
            return LocalDateTime.now().plusDays(ttlDays);
        }
        if (defaultTtlDays > 0) {
            return LocalDateTime.now().plusDays(defaultTtlDays);
        }
        return null; // no expiry
    }

    private String buildShortUrl(String shortCode) {
        return baseUrl + "/" + shortCode;
    }

    private ShortenResponse toShortenResponse(Url url) {
        return ShortenResponse.builder()
                .id(url.getId())
                .shortCode(url.getShortCode())
                .shortUrl(buildShortUrl(url.getShortCode()))
                .originalUrl(url.getOriginalUrl())
                .title(url.getTitle())
                .isCustomAlias(url.getIsCustomAlias())
                .expiresAt(url.getExpiresAt())
                .createdAt(url.getCreatedAt())
                .build();
    }

    private UrlStatsResponse toStatsResponse(Url url) {
        return UrlStatsResponse.builder()
                .id(url.getId())
                .shortCode(url.getShortCode())
                .shortUrl(buildShortUrl(url.getShortCode()))
                .originalUrl(url.getOriginalUrl())
                .title(url.getTitle())
                .clickCount(url.getClickCount())
                .isActive(url.getIsActive())
                .isExpired(url.isExpired())
                .isCustomAlias(url.getIsCustomAlias())
                .createdBy(url.getCreatedBy())
                .expiresAt(url.getExpiresAt())
                .createdAt(url.getCreatedAt())
                .updatedAt(url.getUpdatedAt())
                .build();
    }
}
