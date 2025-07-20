package com.example.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

@SpringBootTest
public class LoadBalancerTest {
    
    @Autowired
    private LoadBalancerClient loadBalancerClient;

    @Test
    void loadBalancerTest() {
        for (int i = 0; i < 10; i++) {
            ServiceInstance serviceInstance = loadBalancerClient.choose("service-product");
            System.out.println("[" + i + "] instance: " + serviceInstance.getHost() + ":" + serviceInstance.getPort());
        }
    }
}
