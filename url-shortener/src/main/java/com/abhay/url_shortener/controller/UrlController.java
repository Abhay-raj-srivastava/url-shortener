package com.abhay.url_shortener.controller;

import com.abhay.url_shortener.dto.request.ShortenRequest;
import com.abhay.url_shortener.dto.request.UpdateUrlRequest;
import com.abhay.url_shortener.dto.response.ApiResponse;
import com.abhay.url_shortener.dto.response.ShortenResponse;
import com.abhay.url_shortener.dto.response.UrlStatsResponse;
import com.abhay.url_shortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * REST controller for URL shortening operations.
 *
 * Endpoint design:
 *
 * POST   /api/v1/urls              — shorten a URL
 * GET    /{shortCode}              — redirect (301/302) to original URL
 * GET    /api/v1/urls/{shortCode}  — get stats/metadata (no redirect)
 * PATCH  /api/v1/urls/{shortCode}  — update a URL record
 * DELETE /api/v1/urls/{shortCode}  — soft-delete a URL
 * GET    /api/v1/urls/top          — top N URLs by click count
 * GET    /api/v1/urls/user/{createdBy} — URLs by creator
 *
 * HTTP 301 vs 302 for redirects:
 * - 301 (Permanent) causes browsers to cache the redirect, reducing
 *   server load but making it impossible to track subsequent clicks or
 *   update the destination.
 * - 302 (Found/Temporary) forces the browser to hit the server every time,
 *   enabling click tracking and dynamic destination updates.
 * - We use 302 here because analytics and the ability to update/expire links
 *   are core requirements.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "URL Shortener", description = "Endpoints for shortening, redirecting, and managing URLs")
public class UrlController {

    private final UrlService urlService;

    // ---------------------------------------------------------------
    //  Shorten
    // ---------------------------------------------------------------

    @Operation(summary = "Shorten a URL", description = "Creates a new shortened URL and returns the short link")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "URL shortened successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Custom alias already in use"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "URL failed semantic validation")
    })
    @PostMapping("/api/v1/urls")
    public ResponseEntity<ApiResponse<ShortenResponse>> shortenUrl(
            @Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = urlService.shortenUrl(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "URL shortened successfully"));
    }

    // ---------------------------------------------------------------
    //  Redirect  (root path — no /api/v1 prefix, no auth required)
    // ---------------------------------------------------------------

    @Operation(
        summary = "Redirect to original URL",
        description = "Resolves a short code and issues a 302 redirect to the original URL"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirect to original URL"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Short code not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "URL has expired")
    })
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "The short code to resolve", example = "aB3xK9z")
            @PathVariable String shortCode) {
        String originalUrl = urlService.resolveUrl(shortCode);
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }

    // ---------------------------------------------------------------
    //  Stats / Metadata
    // ---------------------------------------------------------------

    @Operation(summary = "Get URL statistics", description = "Returns analytics and metadata for a short code without redirecting")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stats retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Short code not found")
    })
    @GetMapping("/api/v1/urls/{shortCode}")
    public ResponseEntity<ApiResponse<UrlStatsResponse>> getStats(
            @Parameter(description = "The short code to look up", example = "aB3xK9z")
            @PathVariable String shortCode) {
        UrlStatsResponse stats = urlService.getStats(shortCode);
        return ResponseEntity.ok(ApiResponse.success(stats, "Stats retrieved successfully"));
    }

    // ---------------------------------------------------------------
    //  Top URLs
    // ---------------------------------------------------------------

    @Operation(summary = "Get top URLs by click count")
    @GetMapping("/api/v1/urls/top")
    public ResponseEntity<ApiResponse<List<UrlStatsResponse>>> getTopUrls(
            @Parameter(description = "Number of results to return (1–100)")
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(100) int limit) {
        List<UrlStatsResponse> top = urlService.getTopUrls(limit);
        return ResponseEntity.ok(ApiResponse.success(top, "Top URLs retrieved successfully"));
    }

    // ---------------------------------------------------------------
    //  URLs by creator
    // ---------------------------------------------------------------

    @Operation(summary = "Get all URLs created by a specific user")
    @GetMapping("/api/v1/urls/user/{createdBy}")
    public ResponseEntity<ApiResponse<List<ShortenResponse>>> getUrlsByCreator(
            @Parameter(description = "Creator identifier")
            @PathVariable String createdBy) {
        List<ShortenResponse> urls = urlService.getUrlsByCreator(createdBy);
        return ResponseEntity.ok(ApiResponse.success(urls, "URLs retrieved successfully"));
    }

    // ---------------------------------------------------------------
    //  Update
    // ---------------------------------------------------------------

    @Operation(summary = "Update a shortened URL", description = "Partially updates a URL record (PATCH semantics — only non-null fields applied)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "URL updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Short code not found")
    })
    @PatchMapping("/api/v1/urls/{shortCode}")
    public ResponseEntity<ApiResponse<ShortenResponse>> updateUrl(
            @PathVariable String shortCode,
            @Valid @RequestBody UpdateUrlRequest request) {
        ShortenResponse updated = urlService.updateUrl(shortCode, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "URL updated successfully"));
    }

    // ---------------------------------------------------------------
    //  Delete
    // ---------------------------------------------------------------

    @Operation(summary = "Delete a shortened URL", description = "Soft-deletes a URL (deactivates it, preserving analytics data)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "URL deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Short code not found")
    })
    @DeleteMapping("/api/v1/urls/{shortCode}")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }
}
