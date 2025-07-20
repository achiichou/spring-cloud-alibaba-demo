package com.example.order.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.order.Order;
import com.example.order.properties.OrderProperties;
import com.example.order.service.OrderService;

@RestController
@RequestMapping("/order")
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
}
