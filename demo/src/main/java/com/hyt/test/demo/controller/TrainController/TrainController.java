package com.hyt.test.demo.controller.TrainController;

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

import com.hyt.test.demo.common.Train;
import com.hyt.test.demo.service.TrainService;

@RestController
@RequestMapping("/train")
public class TrainController {
    @Autowired
    private TrainService trainService;

    @GetMapping("/list")
    public List<Train> list() {
        return trainService.listAll();
    }

    @GetMapping("/{id}")
    public Train getById(@PathVariable Long id) {
        return trainService.getById(id);
    }

    @PostMapping("/add")
    public boolean add(@RequestBody Train train) {
        return trainService.add(train);
    }

    @PutMapping("/update")
    public boolean update(@RequestBody Train train) {
        return trainService.update(train);
    }

    @DeleteMapping("/delete/{id}")
    public boolean delete(@PathVariable Long id) {
        return trainService.delete(id);
    }
}
