package com.abhay.url_shortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * General application configuration.
 *
 * CORS policy:
 * - In development, all origins are allowed to simplify local front-end work.
 * - In production, restrict allowed origins to your actual front-end domain(s)
 *   via the CORS_ALLOWED_ORIGINS environment variable (not done here to keep
 *   the config simple — add it when authentication is introduced).
 * - The redirect endpoint (GET /{shortCode}) is called by browsers following
 *   a link, so it doesn't need CORS headers. CORS headers matter for the API
 *   endpoints called by front-end JS.
 *
 * @EnableScheduling activates the @Scheduled cleanup job in UrlService.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * Global CORS filter applied before Spring MVC dispatcher.
     * Allows the Swagger UI and any front-end SPA to call the API.
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allowed origins — tighten in production
        config.setAllowedOriginPatterns(List.of("*"));

        // Methods exposed to cross-origin callers
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Headers the client is allowed to send
        config.setAllowedHeaders(List.of("*"));

        // Headers the browser is allowed to read from the response
        config.setExposedHeaders(List.of("Location", "Content-Type", "X-Request-Id"));

        // Allow credentials (cookies, Authorization header)
        config.setAllowCredentials(true);

        // Pre-flight response is valid for 1 hour
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
