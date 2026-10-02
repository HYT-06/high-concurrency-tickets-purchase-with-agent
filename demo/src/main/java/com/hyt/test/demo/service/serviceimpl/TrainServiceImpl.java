package com.hyt.test.demo.service.serviceimpl;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.hyt.test.demo.common.CacheKeys;
import com.hyt.test.demo.common.RedisLock;
import com.hyt.test.demo.common.Train;
import com.hyt.test.demo.mapper.TrainMapper;
import com.hyt.test.demo.service.TrainService;

@Service
public class TrainServiceImpl implements TrainService {

    private static final Logger log = LoggerFactory.getLogger(TrainServiceImpl.class);

    /** L2 缓存基线过期时间：30 分钟 */
    private static final long TTL_BASE_SECONDS = 1800;
    /** 过期时间抖动范围 ±5 分钟，用于错开失效时刻，避免大量 key 同时过期（缓存雪崩） */
    private static final int TTL_JITTER_SECONDS = 300;
    /** 缓存重建互斥锁的持有时间 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    /** 未抢到锁时等待其他线程重建缓存的时长 */
    private static final long LOCK_WAIT_MILLIS = 50;

    @Autowired
    private TrainMapper trainMapper;
    @Autowired
    private Cache<String, Object> caffeineCache;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    @Autowired
    private RedisLock redisLock;

    // 车次列表是纯读取、且不带缓存，主从秒级延迟无影响，下沉到从库。
    // getById 不加 readOnly：它负责回填 Caffeine/Redis 缓存，
    // 若从库尚未同步最新车次，会把旧值缓存 25~35 分钟。
    @Override
    @Transactional(readOnly = true)
    public List<Train> listAll() {
        return trainMapper.selectList(null);
    }

    @Override
    public Train getById(Long id) {
        String cacheKey = CacheKeys.train(id);

        // L1：本地 Caffeine
        Train train = (Train) caffeineCache.getIfPresent(cacheKey);
        if (train != null) {
            return train;
        }

        // L2：Redis
        train = (Train) redisTemplate.opsForValue().get(cacheKey);
        if (train != null) {
            caffeineCache.put(cacheKey, train);
            return train;
        }

        // 回源重建：用分布式锁保证同一车次同一时刻只有一个线程查库（防缓存击穿）
        String lockKey = CacheKeys.trainLock(id);
        String token = redisLock.tryLock(lockKey, LOCK_TTL);
        if (token == null) {
            // 未抢到锁：短暂等待后重读缓存，仍未命中才降级直查数据库，避免请求全部压到 DB
            sleepQuietly();
            train = (Train) redisTemplate.opsForValue().get(cacheKey);
            if (train != null) {
                caffeineCache.put(cacheKey, train);
                return train;
            }
            log.warn("未获取到缓存重建锁，降级直查数据库: trainId={}", id);
            return trainMapper.selectById(id);
        }

        try {
            // 双重检查：等待期间可能已被其他线程重建
            train = (Train) redisTemplate.opsForValue().get(cacheKey);
            if (train == null) {
                train = trainMapper.selectById(id);
                if (train != null) {
                    redisTemplate.opsForValue().set(cacheKey, train, jitteredTtl());
                }
            }
            if (train != null) {
                caffeineCache.put(cacheKey, train);
            }
            return train;
        } finally {
            redisLock.unlock(lockKey, token);
        }
    }

    @Override
    public boolean add(Train train) {
        return trainMapper.insert(train) > 0;
    }

    @Override
    public boolean update(Train train) {
        // 先写库再删缓存：避免"先删缓存、后写库"期间被读请求回填旧值
        boolean updated = trainMapper.updateById(train) > 0;
        evict(train.getId());
        return updated;
    }

    @Override
    public boolean delete(Long id) {
        boolean deleted = trainMapper.deleteById(id) > 0;
        evict(id);
        return deleted;
    }

    /** 淘汰 L1 + L2 两级缓存 */
    private void evict(Long id) {
        String cacheKey = CacheKeys.train(id);
        caffeineCache.invalidate(cacheKey);
        redisTemplate.delete(cacheKey);
    }

    /** 带随机抖动的过期时间，取值区间 25~35 分钟 */
    private static Duration jitteredTtl() {
        long seconds = TTL_BASE_SECONDS
                + ThreadLocalRandom.current().nextLong(-TTL_JITTER_SECONDS, TTL_JITTER_SECONDS + 1);
        return Duration.ofSeconds(seconds);
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(LOCK_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}