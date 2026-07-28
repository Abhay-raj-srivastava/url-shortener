package com.abhay.url_shortener.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration for caching and direct RedisTemplate access.
 *
 * Caching strategy:
 * - The "redirects" cache holds originalUrl strings keyed by shortCode.
 *   These entries have a shorter TTL than other caches because they are
 *   the hottest path and we want stale entries to expire naturally even
 *   if a manual CacheEvict is missed.
 * - All cache values are serialized to JSON (not Java serialization) so
 *   cache entries survive application restarts and are human-readable in
 *   Redis CLI.
 * - JavaTimeModule is registered on the ObjectMapper so LocalDateTime
 *   fields serialize/deserialize correctly.
 *
 * Why GenericJackson2JsonRedisSerializer?
 * - It embeds the Java class name in the JSON, allowing Spring to
 *   deserialize the correct type on cache reads without extra configuration.
 * - The alternative (Jackson2JsonRedisSerializer) requires you to specify
 *   the target class upfront, which is less flexible for generic caches.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${app.cache.redirect-ttl-seconds:3600}")
    private long redirectTtlSeconds;

    /**
     * Configures the ObjectMapper used by Redis serializers.
     * JavaTimeModule handles Java 8 date/time types (LocalDateTime, etc.).
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Enable polymorphic type handling so Spring Cache can deserialize
        // the stored class back to the correct type.
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    /**
     * General-purpose RedisTemplate for direct key/value access.
     * Keys are stored as plain strings; values as JSON.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * Spring Cache manager backed by Redis.
     * Per-cache TTL configuration: "redirects" cache gets a shorter TTL
     * than the default.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        // Default cache configuration — 1 hour TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(redirectTtlSeconds))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
            )
            .disableCachingNullValues();

        // Per-cache overrides — add more entries here as new caches are introduced
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("redirects",
            defaultConfig.entryTtl(Duration.ofSeconds(redirectTtlSeconds)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}
