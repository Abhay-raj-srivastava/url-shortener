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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for UrlService.
 *
 * Strategy:
 * - MockitoExtension wires mocks without a Spring context — tests run fast.
 * - ReflectionTestUtils injects @Value fields that Spring would normally inject.
 * - SimpleMeterRegistry is used instead of mocking MeterRegistry — it's a
 *   real no-op implementation that avoids complex counter mock setup.
 * - BDD-style given/when/then structure for readability.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UrlService Unit Tests")
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private UrlValidator urlValidator;

    // Use a real SimpleMeterRegistry — avoids mocking the complex MeterRegistry API
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private UrlService urlService;

    private static final String BASE_URL = "http://localhost:8080";
    private static final String SHORT_CODE = "abc1234";
    private static final String ORIGINAL_URL = "https://www.example.com/some/long/path";

    @BeforeEach
    void setUp() {
        urlService = new UrlService(urlRepository, shortCodeGenerator, urlValidator, meterRegistry);
        ReflectionTestUtils.setField(urlService, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(urlService, "defaultTtlDays", 30);
    }

    // ---------------------------------------------------------------
    //  shortenUrl
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("shortenUrl()")
    class ShortenUrl {

        @Test
        @DisplayName("should create a shortened URL with auto-generated code")
        void shouldShortenUrlWithGeneratedCode() {
            // given
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl(ORIGINAL_URL)
                    .build();

            given(urlValidator.isBlockedUrl(ORIGINAL_URL)).willReturn(false);
            given(shortCodeGenerator.generateRandomCode()).willReturn(SHORT_CODE);
            given(urlRepository.existsByShortCode(SHORT_CODE)).willReturn(false);

            Url savedUrl = buildActiveUrl(SHORT_CODE, ORIGINAL_URL);
            given(urlRepository.save(any(Url.class))).willReturn(savedUrl);

            // when
            ShortenResponse response = urlService.shortenUrl(request);

            // then
            assertThat(response.getShortCode()).isEqualTo(SHORT_CODE);
            assertThat(response.getShortUrl()).isEqualTo(BASE_URL + "/" + SHORT_CODE);
            assertThat(response.getOriginalUrl()).isEqualTo(ORIGINAL_URL);
            assertThat(response.getIsCustomAlias()).isFalse();

            then(urlRepository).should().save(any(Url.class));
        }

        @Test
        @DisplayName("should create a shortened URL with custom alias")
        void shouldShortenUrlWithCustomAlias() {
            // given
            String alias = "my-brand";
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl(ORIGINAL_URL)
                    .customAlias(alias)
                    .build();

            given(urlValidator.isBlockedUrl(ORIGINAL_URL)).willReturn(false);
            given(urlRepository.existsByShortCode(alias)).willReturn(false);

            Url savedUrl = buildActiveUrl(alias, ORIGINAL_URL);
            savedUrl.setIsCustomAlias(true);
            given(urlRepository.save(any(Url.class))).willReturn(savedUrl);

            // when
            ShortenResponse response = urlService.shortenUrl(request);

            // then
            assertThat(response.getShortCode()).isEqualTo(alias);
            assertThat(response.getIsCustomAlias()).isTrue();
            then(shortCodeGenerator).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("should throw CustomAliasAlreadyExistsException when alias is taken")
        void shouldThrowWhenCustomAliasAlreadyExists() {
            // given
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl(ORIGINAL_URL)
                    .customAlias("taken-alias")
                    .build();

            given(urlValidator.isBlockedUrl(ORIGINAL_URL)).willReturn(false);
            given(urlRepository.existsByShortCode("taken-alias")).willReturn(true);

            // when / then
            assertThatThrownBy(() -> urlService.shortenUrl(request))
                    .isInstanceOf(CustomAliasAlreadyExistsException.class)
                    .hasMessageContaining("taken-alias");
        }

        @Test
        @DisplayName("should throw InvalidUrlException when URL host is blocked")
        void shouldThrowWhenUrlIsBlocked() {
            // given
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl("http://localhost/admin")
                    .build();

            given(urlValidator.isBlockedUrl("http://localhost/admin")).willReturn(true);
            given(urlValidator.extractHost("http://localhost/admin")).willReturn("localhost");

            // when / then
            assertThatThrownBy(() -> urlService.shortenUrl(request))
                    .isInstanceOf(InvalidUrlException.class);
        }

        @Test
        @DisplayName("should throw ShortCodeGenerationException after max retries")
        void shouldThrowAfterMaxRetries() {
            // given
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl(ORIGINAL_URL)
                    .build();

            given(urlValidator.isBlockedUrl(ORIGINAL_URL)).willReturn(false);
            // Every generated code is always taken
            given(shortCodeGenerator.generateRandomCode()).willReturn(SHORT_CODE);
            given(urlRepository.existsByShortCode(SHORT_CODE)).willReturn(true);

            // when / then
            assertThatThrownBy(() -> urlService.shortenUrl(request))
                    .isInstanceOf(ShortCodeGenerationException.class);
        }

        @Test
        @DisplayName("should apply custom TTL when provided")
        void shouldApplyCustomTtl() {
            // given
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl(ORIGINAL_URL)
                    .ttlDays(7)
                    .build();

            given(urlValidator.isBlockedUrl(ORIGINAL_URL)).willReturn(false);
            given(shortCodeGenerator.generateRandomCode()).willReturn(SHORT_CODE);
            given(urlRepository.existsByShortCode(SHORT_CODE)).willReturn(false);

            Url savedUrl = buildActiveUrl(SHORT_CODE, ORIGINAL_URL);
            savedUrl.setExpiresAt(LocalDateTime.now().plusDays(7));
            given(urlRepository.save(any(Url.class))).willReturn(savedUrl);

            // when
            ShortenResponse response = urlService.shortenUrl(request);

            // then
            assertThat(response.getExpiresAt()).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    //  resolveUrl / getCachedOriginalUrl
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("resolveUrl() / getCachedOriginalUrl()")
    class ResolveUrl {

        @Test
        @DisplayName("should return original URL for a valid active short code")
        void shouldReturnOriginalUrlForValidCode() {
            // given
            Url url = buildActiveUrl(SHORT_CODE, ORIGINAL_URL);
            given(urlRepository.findByShortCodeAndIsActiveTrue(SHORT_CODE))
                    .willReturn(Optional.of(url));

            // when
            String result = urlService.getCachedOriginalUrl(SHORT_CODE);

            // then
            assertThat(result).isEqualTo(ORIGINAL_URL);
        }

        @Test
        @DisplayName("should throw UrlNotFoundException when code does not exist")
        void shouldThrowWhenCodeNotFound() {
            // given
            given(urlRepository.findByShortCodeAndIsActiveTrue(SHORT_CODE))
                    .willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> urlService.getCachedOriginalUrl(SHORT_CODE))
                    .isInstanceOf(UrlNotFoundException.class)
                    .hasMessageContaining(SHORT_CODE);
        }

        @Test
        @DisplayName("should throw UrlExpiredException for an expired link")
        void shouldThrowForExpiredUrl() {
            // given
            Url url = buildActiveUrl(SHORT_CODE, ORIGINAL_URL);
            url.setExpiresAt(LocalDateTime.now().minusDays(1)); // expired yesterday
            given(urlRepository.findByShortCodeAndIsActiveTrue(SHORT_CODE))
                    .willReturn(Optional.of(url));

            // when / then
            assertThatThrownBy(() -> urlService.getCachedOriginalUrl(SHORT_CODE))
                    .isInstanceOf(UrlExpiredException.class);
        }
    }

    // ---------------------------------------------------------------
    //  getStats
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getStats()")
    class GetStats {

        @Test
        @DisplayName("should return full stats for a valid short code")
        void shouldReturnStats() {
            // given
            Url url = buildActiveUrl(SHORT_CODE, ORIGINAL_URL);
            url.setClickCount(42L);
            given(urlRepository.findByShortCode(SHORT_CODE)).willReturn(Optional.of(url));

            // when
            UrlStatsResponse stats = urlService.getStats(SHORT_CODE);

            // then
            assertThat(stats.getClickCount()).isEqualTo(42L);
            assertThat(stats.getShortCode()).isEqualTo(SHORT_CODE);
            assertThat(stats.getIsActive()).isTrue();
            assertThat(stats.getIsExpired()).isFalse();
        }

        @Test
        @DisplayName("should throw UrlNotFoundException when code not found")
        void shouldThrowWhenNotFound() {
            given(urlRepository.findByShortCode(SHORT_CODE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> urlService.getStats(SHORT_CODE))
                    .isInstanceOf(UrlNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------
    //  updateUrl
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("updateUrl()")
    class UpdateUrl {

        @Test
        @DisplayName("should update originalUrl when provided")
        void shouldUpdateOriginalUrl() {
            // given
            String newUrl = "https://www.updated.com/new-path";
            UpdateUrlRequest request = UpdateUrlRequest.builder()
                    .originalUrl(newUrl)
                    .build();

            Url url = buildActiveUrl(SHORT_CODE, ORIGINAL_URL);
            given(urlRepository.findByShortCode(SHORT_CODE)).willReturn(Optional.of(url));
            given(urlValidator.isBlockedUrl(newUrl)).willReturn(false);
            given(urlRepository.save(any(Url.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            ShortenResponse response = urlService.updateUrl(SHORT_CODE, request);

            // then
            assertThat(response.getOriginalUrl()).isEqualTo(newUrl);
        }

        @Test
        @DisplayName("should not change fields that are null in the request")
        void shouldIgnoreNullFields() {
            // given — only ttlDays is set
            UpdateUrlRequest request = UpdateUrlRequest.builder()
                    .ttlDays(14)
                    .build();

            Url url = buildActiveUrl(SHORT_CODE, ORIGINAL_URL);
            given(urlRepository.findByShortCode(SHORT_CODE)).willReturn(Optional.of(url));
            given(urlRepository.save(any(Url.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            ShortenResponse response = urlService.updateUrl(SHORT_CODE, request);

            // then — originalUrl unchanged
            assertThat(response.getOriginalUrl()).isEqualTo(ORIGINAL_URL);
        }

        @Test
        @DisplayName("should throw UrlNotFoundException when code not found")
        void shouldThrowWhenNotFound() {
            given(urlRepository.findByShortCode(SHORT_CODE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> urlService.updateUrl(SHORT_CODE, new UpdateUrlRequest()))
                    .isInstanceOf(UrlNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------
    //  deleteUrl
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("deleteUrl()")
    class DeleteUrl {

        @Test
        @DisplayName("should soft-delete an active URL")
        void shouldSoftDelete() {
            // given
            Url url = buildActiveUrl(SHORT_CODE, ORIGINAL_URL);
            given(urlRepository.findByShortCode(SHORT_CODE)).willReturn(Optional.of(url));
            given(urlRepository.save(any(Url.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            urlService.deleteUrl(SHORT_CODE);

            // then — isActive flipped to false
            then(urlRepository).should().save(argThat(u -> !u.getIsActive()));
        }

        @Test
        @DisplayName("should throw UrlNotFoundException when code not found")
        void shouldThrowWhenNotFound() {
            given(urlRepository.findByShortCode(SHORT_CODE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> urlService.deleteUrl(SHORT_CODE))
                    .isInstanceOf(UrlNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------
    //  getTopUrls
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getTopUrls()")
    class GetTopUrls {

        @Test
        @DisplayName("should return top URLs and cap limit at 100")
        void shouldCapLimitAt100() {
            // given
            given(urlRepository.findTopByClickCount(100)).willReturn(List.of());

            // when — requesting 200, should be capped to 100
            urlService.getTopUrls(200);

            // then
            then(urlRepository).should().findTopByClickCount(100);
        }
    }

    // ---------------------------------------------------------------
    //  Helper builders
    // ---------------------------------------------------------------

    private Url buildActiveUrl(String shortCode, String originalUrl) {
        return Url.builder()
                .id(1L)
                .shortCode(shortCode)
                .originalUrl(originalUrl)
                .clickCount(0L)
                .isActive(true)
                .isCustomAlias(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
