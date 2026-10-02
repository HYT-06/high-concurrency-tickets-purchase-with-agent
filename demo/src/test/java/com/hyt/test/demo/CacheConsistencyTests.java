package com.hyt.test.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hyt.test.demo.common.CacheKeys;
import com.hyt.test.demo.common.RedisLock;
import com.hyt.test.demo.common.Seat;
import com.hyt.test.demo.common.Train;
import com.hyt.test.demo.config.CacheConfig;
import com.hyt.test.demo.mapper.SeatMapper;
import com.hyt.test.demo.mapper.TrainMapper;
import com.hyt.test.demo.service.SeatService;
import com.hyt.test.demo.service.TrainService;

/**
 * 缓存一致性改造的集成验证，依赖真实的 MySQL 与 Redis。
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class CacheConsistencyTests {

    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private SeatService seatService;
    @Autowired
    private TrainService trainService;
    @Autowired
    private SeatMapper seatMapper;
    @Autowired
    private TrainMapper trainMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    @Autowired
    private com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCache;
    @Autowired
    private RedisLock redisLock;

    /** 注解式缓存必须落到 Redis，而不是被忽略 */
    @Test
    void cacheManagerIsRedis() {
        assertTrue(cacheManager instanceof RedisCacheManager,
                "CacheManager 应为 RedisCacheManager，实际: " + cacheManager.getClass().getName());
        assertNotNull(cacheManager.getCache(CacheConfig.CACHE_SEAT), "seat 缓存未注册");
    }

    /** 启动预热：Redis 库存必须与数据库座位状态一致，且永不过期 */
    @Test
    void preheatBuildsStockFromDatabaseWithoutTtl() {
        List<Seat> seats = seatMapper.selectList(null);
        assertFalse(seats.isEmpty(), "数据库无座位数据，测试无意义");

        for (Seat seat : seats) {
            String stockKey = CacheKeys.seatStock(seat.getId());
            String expected = "available".equalsIgnoreCase(seat.getStatus()) ? "1" : "0";
            assertEquals(expected, stringRedisTemplate.opsForValue().get(stockKey),
                    "库存与数据库不一致: seatId=" + seat.getId() + ", status=" + seat.getStatus());

            Long ttl = stringRedisTemplate.getExpire(stockKey);
            assertEquals(-1L, ttl, "库存 key 是扣减事实来源，不应设置过期时间: " + stockKey);
        }
    }

    /** @Cacheable 写入 Redis，@CacheEvict 能真正清掉同一批 key */
    @Test
    void seatCacheWritesAndEvicts() {
        Long seatId = firstAvailableSeatId();
        Cache seatCache = cacheManager.getCache(CacheConfig.CACHE_SEAT);
        seatCache.clear();

        Seat cached = seatService.getById(seatId);
        assertNotNull(cached, "座位不存在: " + seatId);

        String redisKey = CacheConfig.CACHE_SEAT + "::" + seatId;
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey)),
                "getById 未把结果写入 Redis: " + redisKey);
        Long ttl = stringRedisTemplate.getExpire(redisKey);
        assertTrue(ttl != null && ttl > 0, "注解式缓存应带过期时间，实际 ttl=" + ttl);

        seatService.update(cached);
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey)),
                "update 后缓存未被清除，key 命名仍未对齐: " + redisKey);
    }

    /** 车次缓存：写入 Redis 且 TTL 落在 25~35 分钟的抖动区间内 */
    @Test
    void trainCacheUsesJitteredTtl() {
        Train train = trainMapper.selectList(null).get(0);
        String redisKey = CacheKeys.train(train.getId());
        redisTemplate.delete(redisKey);
        caffeineCache.invalidate(redisKey);

        assertNotNull(trainService.getById(train.getId()), "车次不存在");

        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)), "车次缓存未写入 Redis");
        Long ttl = redisTemplate.getExpire(redisKey);
        assertNotNull(ttl);
        assertTrue(ttl > 25 * 60 - 10 && ttl <= 35 * 60,
                "TTL 不在抖动区间 25~35 分钟内，实际: " + ttl + "s");
    }

    /** 分布式锁：同一时刻只能被一个持有者获取，且非持有者无法释放 */
    @Test
    void redisLockGuaranteesMutualExclusion() {
        String key = "lock:test:mutual-exclusion";
        stringRedisTemplate.delete(key);

        String token = redisLock.tryLock(key, Duration.ofSeconds(10));
        assertNotNull(token, "首次加锁应成功");

        assertNull(redisLock.tryLock(key, Duration.ofSeconds(10)), "锁被占用时不应再次获取成功");

        assertFalse(redisLock.unlock(key, "wrong-token"), "非持有者不应能释放锁");
        assertTrue(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)), "锁被错误释放");

        assertTrue(redisLock.unlock(key, token), "持有者应能释放锁");
        assertFalse(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)), "释放后锁 key 应被删除");
    }

    private Long firstAvailableSeatId() {
        return seatMapper.selectList(null).stream()
                .filter(s -> "available".equalsIgnoreCase(s.getStatus()))
                .map(Seat::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("数据库没有 available 状态的座位"));
    }
}