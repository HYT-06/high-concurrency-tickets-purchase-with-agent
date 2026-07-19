package com.hyt.test.demo.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyt.test.demo.common.Seat;

@Mapper
public interface SeatMapper extends BaseMapper<Seat> {

    @Select("SELECT * FROM seat WHERE train_id = #{trainId}")
    List<Seat> selectByTrainId(Long trainId);

    @Select("SELECT * FROM seat WHERE train_id = #{trainId} AND status = 'available'")
    List<Seat> selectAvailableByTrainId(Long trainId);
}
