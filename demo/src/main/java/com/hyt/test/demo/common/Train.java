package com.hyt.test.demo.common;

import java.io.Serializable;
import lombok.Data;

@Data
public class Train implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private String number;
    private String type;
    private String status;
    private String departure;
    private String arrival;
    private String duration;
    private double price;
    private String date;
}
