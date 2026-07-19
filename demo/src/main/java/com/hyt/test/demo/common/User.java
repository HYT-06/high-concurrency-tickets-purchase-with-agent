package com.hyt.test.demo.common;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

@Data
public class User {
    private Long id;
    @TableField("username")
    private String userName;
    private long phone;
    private String address;
    private String password;
    private int status;
    private String token;
}
