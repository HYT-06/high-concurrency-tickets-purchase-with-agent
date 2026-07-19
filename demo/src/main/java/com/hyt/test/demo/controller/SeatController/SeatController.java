package com.hyt.test.demo.controller.SeatController;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hyt.test.demo.common.Seat;
import com.hyt.test.demo.service.SeatService;

@RestController
@RequestMapping("/seat")
public class SeatController {
    @Autowired
    private SeatService seatService;

    @GetMapping("/train/{trainId}")
    public List<Seat> listByTrainId(@PathVariable Long trainId) {
        return seatService.listByTrainId(trainId);
    }

    @GetMapping("/train/{trainId}/available")
    public List<Seat> listAvailable(@PathVariable Long trainId) {
        return seatService.listAvailableByTrainId(trainId);
    }

    @GetMapping("/{id}")
    public Seat getById(@PathVariable Long id) {
        return seatService.getById(id);
    }

    @PostMapping("/add")
    public boolean add(@RequestBody Seat seat) {
        return seatService.add(seat);
    }

    @PutMapping("/update")
    public boolean update(@RequestBody Seat seat) {
        return seatService.update(seat);
    }

    @DeleteMapping("/delete/{id}")
    public boolean delete(@PathVariable Long id) {
        return seatService.delete(id);
    }
}
