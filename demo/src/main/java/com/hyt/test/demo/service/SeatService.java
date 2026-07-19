package com.hyt.test.demo.service;

import java.util.List;

import com.hyt.test.demo.common.Seat;

public interface SeatService {
    List<Seat> listByTrainId(Long trainId);
    List<Seat> listAvailableByTrainId(Long trainId);
    Seat getById(Long id);
    boolean add(Seat seat);
    boolean update(Seat seat);
    boolean delete(Long id);
}
