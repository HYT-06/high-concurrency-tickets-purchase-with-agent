package com.hyt.test.demo.service.serviceimpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.hyt.test.demo.common.Seat;
import com.hyt.test.demo.config.CacheConfig;
import com.hyt.test.demo.mapper.SeatMapper;
import com.hyt.test.demo.service.SeatService;

@Service
public class SeatServiceImpl implements SeatService {
    @Autowired
    private SeatMapper seatMapper;

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_SEAT, key = "'train:' + #trainId")
    public List<Seat> listByTrainId(Long trainId) {
        return seatMapper.selectByTrainId(trainId);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_SEAT, key = "'train:' + #trainId + ':available'")
    public List<Seat> listAvailableByTrainId(Long trainId) {
        return seatMapper.selectAvailableByTrainId(trainId);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.CACHE_SEAT, key = "#id", sync = true)
    public Seat getById(Long id) {
        return seatMapper.selectById(id);
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_SEAT, allEntries = true)
    public boolean add(Seat seat) {
        return seatMapper.insert(seat) > 0;
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_SEAT, allEntries = true)
    public boolean update(Seat seat) {
        return seatMapper.updateById(seat) > 0;
    }

    @Override
    @CacheEvict(cacheNames = CacheConfig.CACHE_SEAT, allEntries = true)
    public boolean delete(Long id) {
        return seatMapper.deleteById(id) > 0;
    }
}