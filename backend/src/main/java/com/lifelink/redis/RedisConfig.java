package com.lifelink.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.lifelink.donor.DonorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis is a derived index and cache, never the source of truth (spec §7), so
 * every use of it here is written to tolerate the server being unreachable.
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig implements CachingConfigurer {

    /** Spec §7: {@code stats:admin} holds the expensive dashboard aggregate for 5 minutes. */
    public static final String STATS_CACHE = "stats";

    @Bean
    RedisCacheManagerBuilderCustomizer cacheDefaults(ObjectMapper objectMapper) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // Produces "stats:admin" rather than Spring's default "stats::admin".
                .computePrefixWith(cacheName -> cacheName + ":")
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(cacheObjectMapper(objectMapper))));

        return builder -> builder
                .withCacheConfiguration(STATS_CACHE, config.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration(DonorService.DONOR_CACHE, config.entryTtl(Duration.ofHours(1)));
    }

    /**
     * Cache entries are read back as {@code Object}, so the concrete type has
     * to travel with the JSON or every hit deserializes to a LinkedHashMap.
     * Copying the application mapper keeps the date handling consistent with
     * the API, and the validator keeps the typing confined to our own types.
     */
    private static ObjectMapper cacheObjectMapper(ObjectMapper objectMapper) {
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.lifelink.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.time.")
                .build();
        return objectMapper.copy().activateDefaultTyping(
                validator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    }

    /**
     * A Redis outage degrades the dashboard to an uncached query rather than
     * failing the request.
     */
    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache read failed for {}:{} — falling back to the database", cache.getName(), key);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache write failed for {}:{}", cache.getName(), key);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache evict failed for {}:{}", cache.getName(), key);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache clear failed for {}", cache.getName());
            }
        };
    }
}
