package com.hyt.test.demo.service;

import com.hyt.test.demo.common.user;
import com.hyt.test.demo.mapper.UserMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


public interface UserService {
    boolean login(user user);


}
 