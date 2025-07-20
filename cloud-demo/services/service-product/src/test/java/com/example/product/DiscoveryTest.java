package com.example.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.junit.jupiter.api.Test;

@SpringBootTest
public class DiscoveryTest {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Test
    void discoveryClientTest() {
        System.out.println("DiscoveryClient: " + discoveryClient);
        for (String service : discoveryClient.getServices()) {
            System.out.println("Service: " + service);
        }
    }

}
