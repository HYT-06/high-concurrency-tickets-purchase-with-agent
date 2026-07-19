package com.hyt.test.demo.service;

import java.util.List;

import com.hyt.test.demo.common.Order;

public interface OrderService {
    Order createOrder(Long userId, Long trainId, Long seatId);
    boolean deleteOrder(Long orderId);
    Order getById(Long id);
    List<Order> listByUserId(Long userId);
}
