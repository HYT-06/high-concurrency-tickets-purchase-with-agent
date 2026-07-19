package com.hyt.test.demo.service.serviceimpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.hyt.test.demo.common.Seat;
import com.hyt.test.demo.mapper.SeatMapper;
import com.hyt.test.demo.service.SeatService;

@Service
public class SeatServiceImpl implements SeatService {
    @Autowired
    private SeatMapper seatMapper;
    @Autowired
    private Cache<String, Object> caffeineCache;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;



    @Override
    @Cacheable
    public List<Seat> listByTrainId(Long trainId) {
        return seatMapper.selectByTrainId(trainId);
    }

    @Override
    @Cacheable
    public List<Seat> listAvailableByTrainId(Long trainId) {
        return seatMapper.selectAvailableByTrainId(trainId);
    }

    @Override
    @Cacheable
    public Seat getById(Long id) {
        return seatMapper.selectById(id);
    }

    @Override
    public boolean add(Seat seat) {
        return seatMapper.insert(seat) > 0;
    }

    @Override
    public boolean update(Seat seat) {
        caffeineCache.invalidate("seat:" + seat.getId());
        redisTemplate.delete("seat:" + seat.getId());
        return seatMapper.updateById(seat) > 0;
    }

    @Override
    public boolean delete(Long id) {
        caffeineCache.invalidate("seat:" + id);
        redisTemplate.delete("seat:" + id);
        return seatMapper.deleteById(id) > 0;
    }
}
