package com.abhay.url_shortener.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request body for PATCH /api/v1/urls/{shortCode} — update a shortened URL.
 * All fields are optional; only non-null fields are applied.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for updating an existing shortened URL")
public class UpdateUrlRequest {

    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    @Pattern(
        regexp = "^(https?|ftp)://.*",
        message = "URL must start with http://, https://, or ftp://"
    )
    @Schema(description = "New destination URL", nullable = true)
    @JsonProperty("original_url")
    private String originalUrl;

    @Size(max = 500, message = "Title must not exceed 500 characters")
    @Schema(description = "Updated title", nullable = true)
    private String title;

    @Min(value = 1, message = "TTL must be at least 1 day")
    @Max(value = 3650, message = "TTL must not exceed 3650 days")
    @Schema(description = "New TTL in days from now", nullable = true)
    @JsonProperty("ttl_days")
    private Integer ttlDays;

    @Schema(description = "Activate or deactivate the link", nullable = true)
    @JsonProperty("is_active")
    private Boolean isActive;
}
