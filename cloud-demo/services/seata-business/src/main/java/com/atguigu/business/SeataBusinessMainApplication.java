package com.atguigu.business;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;


@EnableDiscoveryClient
@SpringBootApplication
// 開啟feign功能，掃描指定包下的介面，生成代理物件
@EnableFeignClients(basePackages = "com.atguigu.business.feign")
@EnableAspectJAutoProxy
public class SeataBusinessMainApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeataBusinessMainApplication.class, args);
    }
}
