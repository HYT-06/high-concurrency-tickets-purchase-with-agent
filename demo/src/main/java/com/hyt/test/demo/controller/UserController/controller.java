package com.hyt.test.demo.controller.UserController;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hyt.test.demo.common.user;
import com.hyt.test.demo.service.UserService;

@RestController
@RequestMapping("/user")
public class controller {
@Autowired
    private UserService userService;
    @PostMapping("/login")
    public boolean login(@RequestBody user user){
        return userService.login(user);
    }
}
