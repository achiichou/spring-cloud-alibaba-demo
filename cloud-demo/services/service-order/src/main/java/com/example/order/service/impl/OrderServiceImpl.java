package com.example.order.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.order.Order;
import com.example.order.feign.ProductFeignClient;
import com.example.order.service.OrderService;
import com.example.product.Product;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private LoadBalancerClient loadBalancerClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ProductFeignClient productFeignClient;

    @SentinelResource(value = "createOrder", fallback = "createOrderFallback")
    // @SentinelResource(value = "createOrder", blockHandler = "createOrderFallback")
    @Override
    public Order createOrder(Long userId, Long productId) {
        // 取得商品資料
        // Product product = getProductForRemote(productId);
        // Product product = getProductForRemotewithLoadBalancer(productId);
        // Product product = getProductForRemotewithAnnotationLoadBalanced(productId);
        Product product = productFeignClient.getProductById(productId);
        
        // 建立訂單
        return Order.builder()
                .userId(userId)
                .userName("John Doe")
                .address("123 Main St, Anytown, USA")
                .id(1L)
                .totalAmount(product.getPrice().multiply(BigDecimal.valueOf(product.getNum())))
                .productList(Arrays.asList(product))
                .build();
    }

    /**
     * 處理Sentinel @SentinelResource 丟出的 BlockException
     * blockHandler：參數為 BlockException
     */
    public Order createOrderFallback(Long userId, Long productId, BlockException e) {
        return Order.builder()
                .userId(404L)
                .userName("sentinel block exception: " + e.getMessage())
                .address("sentinel block exception: " + e.getMessage())
                .id(404L)
                .totalAmount(BigDecimal.ZERO)
                .productList(Arrays.asList())
                .build();
    }

    /**
     * 處理Sentinel @SentinelResource 丟出的 FallbackException
     * fallback：參數為 Throwable
     */
    public Order createOrderFallback(Long userId, Long productId, Throwable e) {
        return Order.builder()
                .userId(404L)
                .userName("sentinel fallback exception: " + e.getMessage())
                .address("sentinel fallback exception: " + e.getMessage())
                .id(404L)
                .totalAmount(BigDecimal.ZERO)
                .productList(Arrays.asList())
                .build();
    }

    /**
     * annotation @LoadBalanced --> ProductServiceConfig
     * @param productId
     * @return
     */
    private Product getProductForRemotewithAnnotationLoadBalanced(Long productId) {
        String url = "http://service-product/product/" + productId;
        log.info("getProductForRemotewithLoadBalanced url: {}", url);
        return restTemplate.getForObject(url, Product.class);
    }

    /**
     * 使用DiscoveryClient取得商品資料 --> 沒有負載均衡 --> 取得第一個實例
     * @param productId
     * @return
     */
    private Product getProductForRemote(Long productId) {
        List<ServiceInstance> instances = discoveryClient.getInstances("service-product");
        ServiceInstance instance = instances.get(0);
        String url = "http://" + instance.getHost() + ":" + instance.getPort() + "/product/" + productId;

        log.info("getProductForRemote url: {}", url);
        return restTemplate.getForObject(url, Product.class);
    }

    /**
     * 使用LoadBalancerClient取得商品資料
     * @param productId
     * @return
     */
    private Product getProductForRemotewithLoadBalancer(Long productId) {
        ServiceInstance instance = loadBalancerClient.choose("service-product");
        String url = "http://" + instance.getHost() + ":" + instance.getPort() + "/product/" + productId;

        log.info("getProductForRemote url: {}", url);
        return restTemplate.getForObject(url, Product.class);
    }
}
