package com.abhay.url_shortener.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Request body for POST /api/v1/urls — shorten a URL.
 *
 * Validation is handled declaratively via Bean Validation annotations.
 * The global exception handler converts MethodArgumentNotValidException
 * into a structured 400 response so callers always get consistent errors.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for shortening a URL")
public class ShortenRequest {

    @NotBlank(message = "Original URL must not be blank")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    @Pattern(
        regexp = "^(https?|ftp)://.*",
        message = "URL must start with http://, https://, or ftp://"
    )
    @Schema(
        description = "The full URL to shorten",
        example = "https://www.example.com/some/very/long/path?query=value"
    )
    @JsonProperty("original_url")
    private String originalUrl;

    /**
     * Optional custom alias (e.g. "my-brand"). Must be 3–20 alphanumeric
     * characters or hyphens/underscores. Null means auto-generate.
     */
    @Size(min = 3, max = 20, message = "Custom alias must be between 3 and 20 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9_-]*$",
        message = "Custom alias may only contain letters, digits, hyphens, and underscores"
    )
    @Schema(
        description = "Optional custom alias for the short code",
        example = "my-brand",
        nullable = true
    )
    @JsonProperty("custom_alias")
    private String customAlias;

    /**
     * Optional human-readable title, stored for display in analytics.
     */
    @Size(max = 500, message = "Title must not exceed 500 characters")
    @Schema(description = "Optional descriptive title", example = "My Campaign Link", nullable = true)
    private String title;

    /**
     * Number of days until the link expires. Null or 0 = use server default.
     */
    @Min(value = 1, message = "TTL must be at least 1 day")
    @Max(value = 3650, message = "TTL must not exceed 3650 days (10 years)")
    @Schema(description = "Time-to-live in days (1–3650). Omit to use the server default.", nullable = true)
    @JsonProperty("ttl_days")
    private Integer ttlDays;

    /**
     * Optional creator identifier (user ID, email, API key, etc.).
     */
    @Size(max = 255, message = "Created-by identifier must not exceed 255 characters")
    @JsonProperty("created_by")
    @Schema(description = "Optional identifier for the creator", nullable = true)
    private String createdBy;
}
