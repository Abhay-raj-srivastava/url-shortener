package com.abhay.url_shortener.repository;

import com.abhay.url_shortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access layer for the Url entity.
 *
 * Design decisions:
 * - findByShortCode is the hot path (every redirect hits it).
 *   The unique index on short_code makes this an O(log n) index scan.
 * - Bulk expiry cleanup uses a @Modifying JPQL query so Hibernate does
 *   not load entities into memory — one DELETE statement instead of N.
 * - findByCreatedBy supports per-user link dashboards without pagination
 *   for now; a Pageable overload can be added later.
 */
@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    /**
     * Primary redirect lookup — find active link by short code.
     */
    Optional<Url> findByShortCodeAndIsActiveTrue(String shortCode);

    /**
     * Find any link by short code regardless of active state.
     * Used by the service to check code uniqueness before inserting.
     */
    Optional<Url> findByShortCode(String shortCode);

    /**
     * Check if a short code is already taken.
     */
    boolean existsByShortCode(String shortCode);

    /**
     * All links created by a given user, newest first.
     */
    List<Url> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * All active links created by a given user.
     */
    List<Url> findByCreatedByAndIsActiveTrueOrderByCreatedAtDesc(String createdBy);

    /**
     * Increment click count directly in the DB to avoid a read-modify-write
     * round trip in high-concurrency scenarios.
     */
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    void incrementClickCount(@Param("shortCode") String shortCode);

    /**
     * Soft-delete all expired, still-active links.
     * Intended to be called by a scheduled cleanup job.
     */
    @Modifying
    @Query("UPDATE Url u SET u.isActive = false WHERE u.expiresAt IS NOT NULL AND u.expiresAt < :now AND u.isActive = true")
    int deactivateExpiredUrls(@Param("now") LocalDateTime now);

    /**
     * Find links expiring within the next N hours — useful for
     * proactive cache eviction or expiry notifications.
     */
    @Query("SELECT u FROM Url u WHERE u.expiresAt IS NOT NULL AND u.expiresAt BETWEEN :from AND :to AND u.isActive = true")
    List<Url> findUrlsExpiringBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Top N links by click count for analytics.
     */
    @Query("SELECT u FROM Url u WHERE u.isActive = true ORDER BY u.clickCount DESC LIMIT :limit")
    List<Url> findTopByClickCount(@Param("limit") int limit);
}
