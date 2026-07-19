package com.hyt.test.demo.controller.UserController;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hyt.test.demo.common.Result;
import com.hyt.test.demo.common.User;
import com.hyt.test.demo.service.UserService;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String password = body.get("password");
        User user = userService.login(name, password);
        if (user != null) {
            return Map.of("success", true, "user", user);
        }
        return Map.of("success", false, "message", "用户名或密码错误");
    }
    @PostMapping("/register")
    public Result register(@RequestBody User user) {
        boolean result=userService.register(user);
        
        return result?Result.success(user):Result.error("注册失败");
    }
}
