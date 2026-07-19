package com.hyt.test.demo.common;

import java.io.Serializable;
import lombok.Data;

@Data
public class Seat implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long trainId;
    private String seatNumber;
    private String status;
    private String createdAt;
    private String updatedAt;
}
