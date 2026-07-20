package com.abhay.url_shortener.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Uniform API response envelope used by every endpoint.
 *
 * Why an envelope?
 * - Clients get a consistent shape regardless of success or failure.
 * - The "success" flag lets consumers branch on a boolean rather than
 *   parsing HTTP status codes in every SDK.
 * - "message" carries human-readable context useful for logging/debugging.
 * - "data" is generic so any payload type fits without boilerplate wrappers.
 * - @JsonInclude(NON_NULL) suppresses null fields from the JSON output,
 *   keeping successful responses clean (no "errors: null" noise).
 *
 * Example success:
 * {
 *   "success": true,
 *   "message": "URL shortened successfully",
 *   "data": { ... },
 *   "timestamp": "2025-01-01T12:00:00"
 * }
 *
 * Example error:
 * {
 *   "success": false,
 *   "message": "Short code not found",
 *   "error_code": "URL_NOT_FOUND",
 *   "timestamp": "2025-01-01T12:00:00"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public class ApiResponse<T> {

    @Schema(description = "Whether the request succeeded")
    private boolean success;

    @Schema(description = "Human-readable message")
    private String message;

    @Schema(description = "Response payload (present on success)")
    private T data;

    @Schema(description = "Machine-readable error code (present on failure)", nullable = true)
    private String errorCode;

    @Schema(description = "Server-side timestamp of the response")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // ---------------------------------------------------------------
    //  Static factory methods
    // ---------------------------------------------------------------

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "OK");
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
