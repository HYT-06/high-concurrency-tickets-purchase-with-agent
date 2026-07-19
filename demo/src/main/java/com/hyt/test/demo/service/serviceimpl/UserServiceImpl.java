package com.hyt.test.demo.service.serviceimpl;

import org.springframework.beans.factory.annotation.Autowired;

import com.hyt.test.demo.common.User;
import com.hyt.test.demo.mapper.UserMapper;
import com.hyt.test.demo.service.UserService;
import org.springframework.stereotype.Service;
import com.hyt.test.demo.config.JwtConfig;
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtConfig jwtConfig;
        
    @Override
    public User login(String name, String password) {
        User currentUser = userMapper.selectByName(name);
        if (currentUser == null || !currentUser.getPassword().equals(password)) {
            return null;
        }
        // 2. 生成 JWT
        String token = jwtConfig.generateToken(currentUser);
        currentUser.setToken(token);
        // 3. 返回用户信息
        return currentUser;
    }

    @Override
    public Boolean register(User user) {
        // 1. 重复检查
        if (userMapper.selectByName(user.getUserName()) != null) {
            return false;
        }
        // 2. 密码加密（需要先在 Spring 容器里注册 BCryptPasswordEncoder Bean）
        // user.setPassword(passwordEncoder.encode(user.getPassword()));
        // 3. 插入并判断结果
        return userMapper.insert(user) > 0;
    }
}
