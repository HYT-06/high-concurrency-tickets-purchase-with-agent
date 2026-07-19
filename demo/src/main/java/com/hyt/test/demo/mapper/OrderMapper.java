package com.hyt.test.demo.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyt.test.demo.common.Order;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT * FROM `order` WHERE user_id = #{userId}")
    List<Order> selectByUserId(Long userId);

    @Select("SELECT * FROM `order` WHERE train_id = #{trainId}")
    List<Order> selectByTrainId(Long trainId);
}
