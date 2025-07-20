package com.example.order;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;

@EnableDiscoveryClient
@SpringBootApplication
public class OrderMainApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderMainApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(NacosConfigManager nacosConfigManager) {
        return args -> {
            System.out.println("OrderMainApplication started");
            nacosConfigManager.getConfigService().addListener("service-order.properties", "DEFAULT_GROUP", new Listener() {
                
                @Override
                public Executor getExecutor() {
                    return Executors.newFixedThreadPool(4);
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    System.out.println("configInfo: " + configInfo);
                }
            });
        };
    }
}