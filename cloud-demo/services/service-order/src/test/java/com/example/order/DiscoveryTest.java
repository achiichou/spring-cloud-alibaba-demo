package com.example.order;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import com.alibaba.cloud.nacos.discovery.NacosServiceDiscovery;
import com.alibaba.nacos.api.exception.NacosException;

@SpringBootTest
public class DiscoveryTest {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private NacosServiceDiscovery nacosServiceDiscovery;


    @Test
    void nacosDiscoveryPropertiesTest() throws NacosException {
        for (String service : nacosServiceDiscovery.getServices()) {
            System.out.println("Service: " + service);
            List<ServiceInstance> instances = nacosServiceDiscovery.getInstances(service);
            for (ServiceInstance instance : instances) {
                System.out.println("Instance: " + instance.getHost() + ":" + instance.getPort());
            }
        }
    }

    @Test
    void discoveryTest() {
        for (String service : discoveryClient.getServices()) {
            System.out.println("Service: " + service);
            List<ServiceInstance> instances = discoveryClient.getInstances(service);
            for (ServiceInstance instance : instances) {
                System.out.println("Instance: " + instance.getHost() + ":" + instance.getPort());
            }
        }
    }
}
