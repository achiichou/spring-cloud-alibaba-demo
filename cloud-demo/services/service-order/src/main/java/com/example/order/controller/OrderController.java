package com.example.order.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.order.Order;
import com.example.order.properties.OrderProperties;
import com.example.order.service.OrderService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
// @RefreshScope
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderProperties orderProperties;
    // @Value("${order.timeout}")
    // private String timeout;
    // @Value("${order.auto-confirm}")
    // private String orderAutoConfirm;

    @GetMapping("/config")
    public String getConfig() {
        return "order.timeout: " + orderProperties.getTimeout() + ", order.auto-confirm: " + orderProperties.getAutoConfirm() + ", order.dbUrl: " + orderProperties.getDbUrl();
    }

    @GetMapping("/create")
    public Order createOrder(@RequestParam Long userId, @RequestParam Long productId) {
        return orderService.createOrder(userId, productId);
    }

    /**
     * 與 createOrder 一樣，用來測試鏈路模式限流
     * 在 sentinel dashboard 限流時，只選擇 /create-path2 這個來源的鏈路，不會影響到 /create 的鏈路
     */
    @GetMapping("/create-path2")
    public Order createOrderPath2(@RequestParam Long userId, @RequestParam Long productId) {
        return orderService.createOrder(userId, productId);
    }

    @GetMapping("/readDB")
    public String readDB() {
        log.info("readDB");
        return "readDB success";
    }

    @GetMapping("/writeDB")
    public String writeDB() {
        log.info("writeDB");
        return "writeDB success";
    }
}
