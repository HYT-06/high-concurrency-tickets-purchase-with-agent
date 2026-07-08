package com.hyt.test.demo.service.serviceimpl;

import org.springframework.beans.factory.annotation.Autowired;

import com.hyt.test.demo.common.user;
import com.hyt.test.demo.mapper.UserMapper;
import com.hyt.test.demo.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Override
    public boolean login(user user) {
        return userMapper.selectById(user.getId()) != null;
    }
}
