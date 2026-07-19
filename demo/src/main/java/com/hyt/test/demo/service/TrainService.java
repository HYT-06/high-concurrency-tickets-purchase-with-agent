package com.hyt.test.demo.service;

import java.util.List;

import com.hyt.test.demo.common.Train;

public interface TrainService {
    List<Train> listAll();
    Train getById(Long id);
    boolean add(Train train);
    boolean update(Train train);
    boolean delete(Long id);
}
