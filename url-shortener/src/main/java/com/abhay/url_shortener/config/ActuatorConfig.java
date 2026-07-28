package com.abhay.url_shortener.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Micrometer / Prometheus metrics configuration.
 *
 * What is instrumented:
 * - All HTTP requests (via Spring Boot auto-configuration of Micrometer)
 * - Custom counters in UrlService (urls created, redirected, expired)
 * - @Timed annotations on methods (enabled via TimedAspect bean)
 * - JVM metrics, thread pool metrics, HikariCP pool metrics — all
 *   auto-configured by Spring Boot Actuator
 *
 * Prometheus scrapes /actuator/prometheus. The docker-compose and
 * Kubernetes configurations point Prometheus at this endpoint.
 *
 * Why common tags?
 * - All metrics will include application=url-shortener, allowing
 *   Grafana dashboards to filter by application when multiple services
 *   report to the same Prometheus instance.
 */
@Configuration
public class ActuatorConfig {

    /**
     * Adds common tags to every metric emitted by this application.
     * Configured in application.properties as:
     *   management.metrics.tags.application=${spring.application.name}
     * This bean provides the programmatic equivalent and is kept for
     * explicitness.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags(List.of(
                Tag.of("application", "url-shortener"),
                Tag.of("region", System.getenv().getOrDefault("REGION", "local"))
            ));
    }

    /**
     * Enables the @Timed annotation on Spring beans.
     * Add @Timed(value = "method.name", description = "...") to any
     * service method to get automatic timing histograms in Prometheus.
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * Denies metrics that are only relevant during integration testing.
     * Add metric name prefixes to keep Prometheus scrapes clean.
     */
    @Bean
    public MeterFilter denyTestMetrics() {
        return MeterFilter.denyNameStartsWith("jvm.classes.unloaded");
    }
}
