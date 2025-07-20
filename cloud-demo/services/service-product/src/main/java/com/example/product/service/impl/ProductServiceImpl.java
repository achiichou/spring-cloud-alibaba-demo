package com.example.product.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.example.product.Product;
import com.example.product.service.ProductService;

@Service
public class ProductServiceImpl implements ProductService {

    @Override
    public Product getProductById(Long id) {
        return Product.builder()
                .id(id)
                .productName("Product " + id)
                .price(new BigDecimal(100))
                .num(2)
                .build();
    }
}
