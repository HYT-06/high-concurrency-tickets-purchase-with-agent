package com.hyt.test.demo.service;

import com.hyt.test.demo.common.User;

public interface UserService {
    User login(String name, String password);
    Boolean register(User user);
}