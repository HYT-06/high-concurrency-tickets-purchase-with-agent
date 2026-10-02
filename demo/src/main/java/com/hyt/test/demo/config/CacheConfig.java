package com.hyt.test.demo.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 注解式缓存配置。
 * <p>
 * 注意：需要显式声明 CacheManager，否则 @Cacheable 解析不出缓存名会直接抛
 * IllegalStateException: No cache could be resolved。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 座位相关缓存的 cache 名，所有 @Cacheable/@CacheEvict 统一使用它 */
    public static final String CACHE_SEAT = "seat";

    /** 只读缓存统一 30 分钟过期，避免像以前那样永不过期导致脏数据长期驻留 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                // Redis 中以明文 key 存储，方便 redis-cli 与监控端排查
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // Spring Data Redis 4.1 默认异步写缓存，删除类命令是 fire-and-forget，
        // 在当前 Redis 3.0.504 上会被静默丢弃，导致 @CacheEvict/clear 失效（缓存删不掉）。
        // 库存/座位这类数据要求淘汰语义可靠，因此改用立即写。
        RedisCacheWriter writer = RedisCacheWriter.create(connectionFactory,
                configurer -> configurer.immediateWrites(true));

        return RedisCacheManager.builder(writer)
                .cacheDefaults(config)
                .build();
    }
}