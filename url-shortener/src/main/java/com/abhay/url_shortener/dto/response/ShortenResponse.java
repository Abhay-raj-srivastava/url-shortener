package com.abhay.url_shortener.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Response body returned after successfully shortening a URL.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response payload after shortening a URL")
public class ShortenResponse {

    @Schema(description = "Database ID of the shortened URL record")
    private Long id;

    @Schema(description = "The generated or custom short code", example = "aB3xK9z")
    @JsonProperty("short_code")
    private String shortCode;

    @Schema(description = "Full short link ready to share", example = "https://short.ly/aB3xK9z")
    @JsonProperty("short_url")
    private String shortUrl;

    @Schema(description = "The original destination URL")
    @JsonProperty("original_url")
    private String originalUrl;

    @Schema(description = "Optional title")
    private String title;

    @Schema(description = "Whether the code is a custom alias")
    @JsonProperty("is_custom_alias")
    private Boolean isCustomAlias;

    @Schema(description = "When this link expires. Null means it never expires.", nullable = true)
    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @Schema(description = "When the record was created")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
