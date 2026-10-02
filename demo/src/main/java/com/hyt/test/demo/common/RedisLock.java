package com.hyt.test.demo.common;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的分布式锁：SET NX PX 获取，Lua 比对 token 后释放。
 * 用于缓存回源时互斥重建，防止热点 key 失效瞬间大量请求同时打到数据库（缓存击穿）。
 */
@Component
public class RedisLock {

    /** 释放锁：只有持有者才能删，避免误删他人的锁 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = createUnlockScript();

    private final StringRedisTemplate stringRedisTemplate;

    public RedisLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static DefaultRedisScript<Long> createUnlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 尝试获取锁，成功返回持有凭证 token，失败返回 null。
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /**
     * 释放锁，只有 token 匹配（即当前持有者）才真正删除。
     */
    public boolean unlock(String key, String token) {
        if (token == null) {
            return false;
        }
        Long result = stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
        return result != null && result > 0;
    }
}