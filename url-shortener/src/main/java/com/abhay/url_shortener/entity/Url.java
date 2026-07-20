package com.abhay.url_shortener.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Core domain entity representing a shortened URL.
 *
 * Design decisions:
 * - shortCode is unique and indexed — it is the primary lookup key for
 *   every redirect. A dedicated index avoids full-table scans under load.
 * - originalUrl is stored as TEXT (mapped to String without @Column length
 *   limit) because URLs can exceed 255 characters.
 * - clickCount uses optimistic updates via the service layer rather than
 *   a DB-level counter to avoid hot-row contention at high traffic.
 * - expiresAt being null means the link never expires.
 * - customAlias flag lets the system know not to reclaim this short code
 *   even after expiry without explicit user action.
 */
@Entity
@Table(
    name = "urls",
    indexes = {
        @Index(name = "idx_urls_short_code", columnList = "short_code", unique = true),
        @Index(name = "idx_urls_expires_at", columnList = "expires_at"),
        @Index(name = "idx_urls_created_by", columnList = "created_by")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The unique short code (e.g. "aB3xK9z").
     * Max 20 chars covers both default 7-char codes and custom aliases.
     */
    @Column(name = "short_code", nullable = false, length = 20, unique = true)
    private String shortCode;

    /**
     * The full destination URL. Stored as TEXT to support long URLs.
     */
    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    /**
     * Optional title for display purposes (e.g. in analytics dashboards).
     */
    @Column(name = "title", length = 500)
    private String title;

    /**
     * Total number of times this short link has been accessed.
     * Incremented by the service on every redirect.
     */
    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    /**
     * When this link expires. Null = no expiry.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Whether the short code was supplied by the user (custom alias).
     * Custom aliases are never auto-recycled.
     */
    @Column(name = "is_custom_alias", nullable = false)
    @Builder.Default
    private Boolean isCustomAlias = false;

    /**
     * Whether this link has been soft-deleted / deactivated.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Optional identifier for the creator (user ID, email, API key, etc.).
     * Kept as a plain string for now; can be replaced with a FK to a User
     * entity when authentication is added.
     */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ---------------------------------------------------------------
    //  Domain helpers
    // ---------------------------------------------------------------

    /**
     * Returns true if the link has passed its expiry date/time.
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Returns true if the link can be used for redirection.
     */
    public boolean isUsable() {
        return Boolean.TRUE.equals(isActive) && !isExpired();
    }

    /**
     * Increments click count by one.
     */
    public void incrementClickCount() {
        this.clickCount++;
    }
}
