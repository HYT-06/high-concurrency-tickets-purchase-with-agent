package com.hyt.test.demo.common;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

@TableName("`order`")
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long trainId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long seatId;
    private double price;
    private String date;
    private String status;
    private String createdAt;
    private String updatedAt;

    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getTrainId() { return trainId; }
    public void setTrainId(Long trainId) { this.trainId = trainId; }
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
