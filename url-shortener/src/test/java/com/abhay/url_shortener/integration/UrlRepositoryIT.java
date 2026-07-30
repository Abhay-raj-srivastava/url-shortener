package com.abhay.url_shortener.integration;

import com.abhay.url_shortener.entity.Url;
import com.abhay.url_shortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for UrlRepository using a real PostgreSQL database
 * running in a Docker container via Testcontainers.
 *
 * Why Testcontainers?
 * - Tests run against the same database engine used in production.
 * - No mocking of database behaviour — SQL, indexes, and constraints
 *   are fully exercised.
 * - The container starts fresh per test class, giving a clean slate.
 * - @DataJpaTest loads only the persistence slice (no web layer, no Redis),
 *   keeping startup fast.
 *
 * @AutoConfigureTestDatabase(replace = NONE) prevents Spring from
 * replacing the configured DataSource with an in-memory H2 database.
 * Combined with @DynamicPropertySource, it points at the Testcontainer.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UrlRepository Integration Tests")
class UrlRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("urlshortener_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Use create-drop for tests — Flyway is disabled in DataJpaTest slice
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private UrlRepository urlRepository;

    private static final String CODE_A = "abc1234";
    private static final String CODE_B = "xyz9999";
    private static final String ORIGINAL_URL = "https://www.example.com/path";

    @BeforeEach
    void setUp() {
        urlRepository.deleteAll();
    }

    @Test
    @DisplayName("should save and retrieve a URL by short code")
    void shouldSaveAndRetrieveByShortCode() {
        // given
        Url url = buildUrl(CODE_A, ORIGINAL_URL, true, null);

        // when
        urlRepository.save(url);
        Optional<Url> found = urlRepository.findByShortCodeAndIsActiveTrue(CODE_A);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getOriginalUrl()).isEqualTo(ORIGINAL_URL);
    }

    @Test
    @DisplayName("should not find inactive URL by active-only query")
    void shouldNotFindInactiveUrl() {
        // given
        Url url = buildUrl(CODE_A, ORIGINAL_URL, false, null);
        urlRepository.save(url);

        // when
        Optional<Url> found = urlRepository.findByShortCodeAndIsActiveTrue(CODE_A);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should report existsByShortCode correctly")
    void shouldCheckExistsByShortCode() {
        urlRepository.save(buildUrl(CODE_A, ORIGINAL_URL, true, null));

        assertThat(urlRepository.existsByShortCode(CODE_A)).isTrue();
        assertThat(urlRepository.existsByShortCode("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("should increment click count via JPQL update")
    void shouldIncrementClickCount() {
        // given
        Url saved = urlRepository.save(buildUrl(CODE_A, ORIGINAL_URL, true, null));
        assertThat(saved.getClickCount()).isZero();

        // when
        urlRepository.incrementClickCount(CODE_A);
        urlRepository.incrementClickCount(CODE_A);

        // then
        Url updated = urlRepository.findByShortCode(CODE_A).orElseThrow();
        assertThat(updated.getClickCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("should deactivate expired URLs")
    void shouldDeactivateExpiredUrls() {
        // given — one expired, one not
        Url expired = buildUrl(CODE_A, ORIGINAL_URL, true, LocalDateTime.now().minusDays(1));
        Url active  = buildUrl(CODE_B, ORIGINAL_URL, true, LocalDateTime.now().plusDays(10));
        urlRepository.saveAll(List.of(expired, active));

        // when
        int count = urlRepository.deactivateExpiredUrls(LocalDateTime.now());

        // then
        assertThat(count).isEqualTo(1);
        assertThat(urlRepository.findByShortCode(CODE_A).map(Url::getIsActive)).contains(false);
        assertThat(urlRepository.findByShortCode(CODE_B).map(Url::getIsActive)).contains(true);
    }

    @Test
    @DisplayName("should return URLs by creator ordered by createdAt desc")
    void shouldReturnUrlsByCreator() {
        // given
        Url first  = buildUrl(CODE_A, ORIGINAL_URL, true, null);
        first.setCreatedBy("user-1");
        Url second = buildUrl(CODE_B, ORIGINAL_URL, true, null);
        second.setCreatedBy("user-1");
        urlRepository.saveAll(List.of(first, second));

        // when
        List<Url> results = urlRepository.findByCreatedByAndIsActiveTrueOrderByCreatedAtDesc("user-1");

        // then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(u -> "user-1".equals(u.getCreatedBy()));
    }

    @Test
    @DisplayName("findUrlsExpiringBetween should return URLs in the time window")
    void shouldFindUrlsExpiringBetween() {
        // given
        LocalDateTime soonExpiry = LocalDateTime.now().plusHours(2);
        Url soonExpiring = buildUrl(CODE_A, ORIGINAL_URL, true, soonExpiry);
        Url farExpiring  = buildUrl(CODE_B, ORIGINAL_URL, true, LocalDateTime.now().plusDays(30));
        urlRepository.saveAll(List.of(soonExpiring, farExpiring));

        // when
        List<Url> results = urlRepository.findUrlsExpiringBetween(
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(4)
        );

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getShortCode()).isEqualTo(CODE_A);
    }

    // ---------------------------------------------------------------
    //  Helper
    // ---------------------------------------------------------------

    private Url buildUrl(String shortCode, String originalUrl, boolean isActive, LocalDateTime expiresAt) {
        return Url.builder()
                .shortCode(shortCode)
                .originalUrl(originalUrl)
                .isActive(isActive)
                .isCustomAlias(false)
                .clickCount(0L)
                .expiresAt(expiresAt)
                .build();
    }
}
