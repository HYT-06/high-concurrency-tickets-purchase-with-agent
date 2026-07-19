package com.hyt.test.demo.service.serviceimpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.hyt.test.demo.common.Train;
import com.hyt.test.demo.mapper.TrainMapper;
import com.hyt.test.demo.service.TrainService;

@Service
public class TrainServiceImpl implements TrainService {
    @Autowired
    private TrainMapper trainMapper;
    @Autowired
    private Cache<String, Object> caffeineCache;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Override
    public List<Train> listAll() {
        return trainMapper.selectList(null);
    }

    @Override
    public Train getById(Long id) {
        // 先从 L1 缓存获取
        Train train = (Train) caffeineCache.getIfPresent("train:" + id);
        if (train == null) {
            // 再从 L2 缓存（Redis）获取
            train = (Train) redisTemplate.opsForValue().get("train:" + id);
            if (train != null) {
                caffeineCache.put("train:" + id, train);
            } else {
                // 最后从数据库获取
                train = trainMapper.selectById(id);
                if (train != null) {
                    redisTemplate.opsForValue().set("train:" + id, train);
                    caffeineCache.put("train:" + id, train);
                }
            }
        }
        return train;
    }

    @Override
    public boolean add(Train train) {
        return trainMapper.insert(train) > 0;
    }

    @Override
    public boolean update(Train train) {
        // 先从 L1 缓存中删除
        caffeineCache.invalidate("train:" + train.getId());
        // 再从 L2 缓存中删除
        redisTemplate.delete("train:" + train.getId());
        return trainMapper.updateById(train) > 0;
    }

    @Override
    public boolean delete(Long id) {
        // 先从 L1 缓存中删除
        caffeineCache.invalidate("train:" + id);
        // 再从 L2 缓存中删除
        redisTemplate.delete("train:" + id);
        // 最后从数据库删除
        return trainMapper.deleteById(id) > 0;
    }
}
