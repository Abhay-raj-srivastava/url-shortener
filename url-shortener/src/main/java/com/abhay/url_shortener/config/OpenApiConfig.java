package com.abhay.url_shortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Springdoc OpenAPI configuration.
 * Swagger UI is available at /swagger-ui.html
 * Raw OpenAPI spec is available at /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI urlShortenerOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("URL Shortener API")
                .description("""
                    Production-grade URL Shortener Service.
                    
                    ## Features
                    - Shorten long URLs with optional custom aliases
                    - 302 redirects with click tracking
                    - URL expiration (TTL)
                    - Analytics per short link
                    - Redis caching on the redirect path
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Abhay")
                    .url("https://github.com/abhay"))
                .license(new License()
                    .name("MIT License")
                    .url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server().url(baseUrl).description("Current server"),
                new Server().url("http://localhost:8080").description("Local development")
            ));
    }
}
