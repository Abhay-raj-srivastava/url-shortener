package com.abhay.url_shortener.controller;

import com.abhay.url_shortener.dto.request.ShortenRequest;
import com.abhay.url_shortener.dto.request.UpdateUrlRequest;
import com.abhay.url_shortener.dto.response.ShortenResponse;
import com.abhay.url_shortener.dto.response.UrlStatsResponse;
import com.abhay.url_shortener.exception.*;
import com.abhay.url_shortener.service.UrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice tests using @WebMvcTest.
 *
 * @WebMvcTest loads only the web layer (controllers, filters, exception handlers).
 * The service is mocked via @MockBean — no database or Redis involved.
 *
 * What these tests verify:
 * - Correct HTTP status codes for each endpoint
 * - Request body validation (400 responses for bad input)
 * - Correct response JSON structure
 * - Exception-to-HTTP-status mappings via GlobalExceptionHandler
 * - Redirect behaviour (302 + Location header)
 */
@WebMvcTest(UrlController.class)
@DisplayName("UrlController Web Layer Tests")
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    private ObjectMapper objectMapper;

    private static final String SHORT_CODE = "abc1234";
    private static final String ORIGINAL_URL = "https://www.example.com/some/long/path";
    private static final String SHORT_URL = "http://localhost:8080/" + SHORT_CODE;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ---------------------------------------------------------------
    //  POST /api/v1/urls
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/urls")
    class ShortenUrl {

        @Test
        @DisplayName("should return 201 with short link on valid request")
        void shouldReturn201OnValidRequest() throws Exception {
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl(ORIGINAL_URL)
                    .build();

            ShortenResponse response = buildShortenResponse(SHORT_CODE);
            given(urlService.shortenUrl(any(ShortenRequest.class))).willReturn(response);

            mockMvc.perform(post("/api/v1/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.short_code").value(SHORT_CODE))
                    .andExpect(jsonPath("$.data.short_url").value(SHORT_URL))
                    .andExpect(jsonPath("$.data.original_url").value(ORIGINAL_URL));
        }

        @Test
        @DisplayName("should return 400 when originalUrl is blank")
        void shouldReturn400WhenOriginalUrlIsBlank() throws Exception {
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl("")
                    .build();

            mockMvc.perform(post("/api/v1/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should return 400 when URL has no http/https scheme")
        void shouldReturn400WhenUrlHasNoScheme() throws Exception {
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl("www.example.com/path")
                    .build();

            mockMvc.perform(post("/api/v1/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("should return 409 when custom alias is already taken")
        void shouldReturn409WhenAliasAlreadyExists() throws Exception {
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl(ORIGINAL_URL)
                    .customAlias("taken")
                    .build();

            given(urlService.shortenUrl(any())).willThrow(new CustomAliasAlreadyExistsException("taken"));

            mockMvc.perform(post("/api/v1/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ALIAS_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("should return 422 when URL points to a blocked host")
        void shouldReturn422WhenUrlIsBlocked() throws Exception {
            ShortenRequest request = ShortenRequest.builder()
                    .originalUrl("http://localhost/admin")
                    .build();

            given(urlService.shortenUrl(any())).willThrow(new InvalidUrlException("Blocked host"));

            mockMvc.perform(post("/api/v1/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_URL"));
        }
    }

    // ---------------------------------------------------------------
    //  GET /{shortCode} — redirect
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("GET /{shortCode} (redirect)")
    class Redirect {

        @Test
        @DisplayName("should return 302 with Location header for valid code")
        void shouldReturn302WithLocationHeader() throws Exception {
            given(urlService.resolveUrl(SHORT_CODE)).willReturn(ORIGINAL_URL);

            mockMvc.perform(get("/" + SHORT_CODE))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", ORIGINAL_URL));
        }

        @Test
        @DisplayName("should return 404 when short code not found")
        void shouldReturn404WhenCodeNotFound() throws Exception {
            given(urlService.resolveUrl(SHORT_CODE)).willThrow(new UrlNotFoundException(SHORT_CODE));

            mockMvc.perform(get("/" + SHORT_CODE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("URL_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return 410 when URL has expired")
        void shouldReturn410WhenUrlExpired() throws Exception {
            given(urlService.resolveUrl(SHORT_CODE)).willThrow(new UrlExpiredException(SHORT_CODE));

            mockMvc.perform(get("/" + SHORT_CODE))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.errorCode").value("URL_EXPIRED"));
        }
    }

    // ---------------------------------------------------------------
    //  GET /api/v1/urls/{shortCode} — stats
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/urls/{shortCode}")
    class GetStats {

        @Test
        @DisplayName("should return 200 with stats for valid code")
        void shouldReturn200WithStats() throws Exception {
            UrlStatsResponse stats = buildStatsResponse(SHORT_CODE);
            given(urlService.getStats(SHORT_CODE)).willReturn(stats);

            mockMvc.perform(get("/api/v1/urls/" + SHORT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.short_code").value(SHORT_CODE))
                    .andExpect(jsonPath("$.data.click_count").value(100));
        }

        @Test
        @DisplayName("should return 404 when code not found")
        void shouldReturn404WhenNotFound() throws Exception {
            given(urlService.getStats(SHORT_CODE)).willThrow(new UrlNotFoundException(SHORT_CODE));

            mockMvc.perform(get("/api/v1/urls/" + SHORT_CODE))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    //  PATCH /api/v1/urls/{shortCode}
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /api/v1/urls/{shortCode}")
    class UpdateUrl {

        @Test
        @DisplayName("should return 200 with updated URL")
        void shouldReturn200WithUpdatedUrl() throws Exception {
            UpdateUrlRequest request = UpdateUrlRequest.builder()
                    .originalUrl("https://www.newurl.com/path")
                    .build();

            ShortenResponse updated = buildShortenResponse(SHORT_CODE);
            updated.setOriginalUrl("https://www.newurl.com/path");
            given(urlService.updateUrl(eq(SHORT_CODE), any(UpdateUrlRequest.class))).willReturn(updated);

            mockMvc.perform(patch("/api/v1/urls/" + SHORT_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ---------------------------------------------------------------
    //  DELETE /api/v1/urls/{shortCode}
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/urls/{shortCode}")
    class DeleteUrl {

        @Test
        @DisplayName("should return 204 on successful deletion")
        void shouldReturn204OnDelete() throws Exception {
            willDoNothing().given(urlService).deleteUrl(SHORT_CODE);

            mockMvc.perform(delete("/api/v1/urls/" + SHORT_CODE))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("should return 404 when code not found")
        void shouldReturn404WhenNotFound() throws Exception {
            willThrow(new UrlNotFoundException(SHORT_CODE)).given(urlService).deleteUrl(SHORT_CODE);

            mockMvc.perform(delete("/api/v1/urls/" + SHORT_CODE))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    private ShortenResponse buildShortenResponse(String shortCode) {
        return ShortenResponse.builder()
                .id(1L)
                .shortCode(shortCode)
                .shortUrl(SHORT_URL)
                .originalUrl(ORIGINAL_URL)
                .isCustomAlias(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UrlStatsResponse buildStatsResponse(String shortCode) {
        return UrlStatsResponse.builder()
                .id(1L)
                .shortCode(shortCode)
                .shortUrl(SHORT_URL)
                .originalUrl(ORIGINAL_URL)
                .clickCount(100L)
                .isActive(true)
                .isExpired(false)
                .isCustomAlias(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
