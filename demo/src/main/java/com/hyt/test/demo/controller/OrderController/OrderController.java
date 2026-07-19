package com.hyt.test.demo.controller.OrderController;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hyt.test.demo.common.Order;
import com.hyt.test.demo.service.OrderService;

@RestController
@RequestMapping("/order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        Long trainId = Long.valueOf(body.get("trainId").toString());
        Long seatId = Long.valueOf(body.get("seatId").toString());
        try {
            Order order = orderService.createOrder(userId, trainId, seatId);
            return Map.of("success", true, "order", order);
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        try {
            boolean result = orderService.deleteOrder(id);
            return Map.of("success", result);
        } catch (RuntimeException e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Order getById(@PathVariable Long id) {
        return orderService.getById(id);
    }

    @GetMapping("/user/{userId}")
    public List<Order> listByUserId(@PathVariable Long userId) {
        return orderService.listByUserId(userId);
    }
}
