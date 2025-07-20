package com.example.order.service;

import com.example.order.Order;

public interface OrderService {

    public Order createOrder(Long userId, Long productId);

}
