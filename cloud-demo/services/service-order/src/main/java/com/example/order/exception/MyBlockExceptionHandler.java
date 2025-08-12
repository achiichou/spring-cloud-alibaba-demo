package com.example.order.exception;

import java.io.PrintWriter;

import org.springframework.stereotype.Component;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 處理"接口層"Sentinel BlockException
 */
@Component
public class MyBlockExceptionHandler implements BlockExceptionHandler {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, String resourceName, BlockException e) throws Exception {
        response.setContentType("application/json;charset=UTF-8");

        PrintWriter writer = response.getWriter();
        Result result = Result.error(500, "sentinel error block:" + resourceName);

        writer.write(objectMapper.writeValueAsString(result));
        writer.flush();
        writer.close();
    }
}
