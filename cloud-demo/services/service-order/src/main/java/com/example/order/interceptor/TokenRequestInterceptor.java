package com.example.order.interceptor;

import java.util.UUID;

import org.springframework.stereotype.Component;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 自訂攔截器，在調用product服務時，添加X-Token頭
 */
// @Component  // 也可以不使用Component，直接在application-feign.yml中配置
public class TokenRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        template.header("X-Token", UUID.randomUUID().toString());
    }
}
