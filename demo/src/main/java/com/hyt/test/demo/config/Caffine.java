package com.hyt.test.demo.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class Caffine {

    @Bean
    public Cache<String, Object> caffeineCache() {
        return Caffeine.newBuilder()
                .initialCapacity(1000)
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }
}
