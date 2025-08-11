package com.example.order.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import feign.Logger;
import feign.Retryer;

@Configuration
public class OrderConfig {

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100, 1000, 3);
    }

    /**
     * 設定完後還需要調整logging level，讓spring boot顯示日誌
     * FULL: 顯示所有日誌
     * BASIC: 顯示基本日誌
     * HEADERS: 顯示基本日誌以及請求和回應頭
     * NONE: 不顯示任何日誌
     * --> 已經移到 application-feign.yml 中
     */
    // @Bean 
    // public Logger.Level feignLoggerLevel() {
    //     return Logger.Level.FULL;
    // }

    @LoadBalanced
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
