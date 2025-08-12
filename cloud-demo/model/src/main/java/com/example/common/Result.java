package com.example.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    private Integer code;
    private String msg;
    private Object data;

    public static Result ok() {
        return new Result(200, "success", null);
    }

    public static Result ok(String msg, Object data) {
        return new Result(200, msg, data);
    }

    public static Result error() {
        return new Result(500, "error", null);
    }

    public static Result error(Integer code, String msg) {
        return new Result(code, msg, null);
    }
}

