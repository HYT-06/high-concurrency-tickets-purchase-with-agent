package com.hyt.test.demo.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Result {
    private String code;
    private String msg;
    private String message;
    private Object data;

    public static Result success(Object data) {
        return Result.builder()
                .code("OK")
                .msg("success")
                .message("success")
                .data(data)
                .build();
    }

    public static Result error(String message) {
        return Result.builder()
                .code("ERROR")
                .msg(message)
                .message(message)
                .data(null)
                .build();
    }
}
