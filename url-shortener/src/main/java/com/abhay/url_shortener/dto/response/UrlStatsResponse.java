package com.abhay.url_shortener.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Analytics / stats response for a specific short code.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Analytics and metadata for a shortened URL")
public class UrlStatsResponse {

    @Schema(description = "Database ID")
    private Long id;

    @Schema(description = "Short code", example = "aB3xK9z")
    @JsonProperty("short_code")
    private String shortCode;

    @Schema(description = "Full short URL", example = "https://short.ly/aB3xK9z")
    @JsonProperty("short_url")
    private String shortUrl;

    @Schema(description = "Original destination URL")
    @JsonProperty("original_url")
    private String originalUrl;

    @Schema(description = "Optional title")
    private String title;

    @Schema(description = "Total number of redirects")
    @JsonProperty("click_count")
    private Long clickCount;

    @Schema(description = "Whether the link is currently active")
    @JsonProperty("is_active")
    private Boolean isActive;

    @Schema(description = "Whether the link has expired")
    @JsonProperty("is_expired")
    private Boolean isExpired;

    @Schema(description = "Whether this is a custom alias")
    @JsonProperty("is_custom_alias")
    private Boolean isCustomAlias;

    @Schema(description = "Creator identifier", nullable = true)
    @JsonProperty("created_by")
    private String createdBy;

    @Schema(description = "Expiry timestamp", nullable = true)
    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @Schema(description = "Creation timestamp")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
