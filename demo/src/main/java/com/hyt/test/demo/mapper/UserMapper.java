package com.hyt.test.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyt.test.demo.common.User;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM user WHERE username = #{name}")
    User selectByName(String name);
}
