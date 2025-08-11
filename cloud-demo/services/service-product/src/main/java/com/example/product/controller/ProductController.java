package com.example.product.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.product.Product;
import com.example.product.service.ProductService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/product/{id}")
    public Product getProduct(@PathVariable Long id, HttpServletRequest request) {
        // feign 攔截器測試
        String xToken = request.getHeader("X-Token");
        System.out.println("xToken: " + xToken);

        return productService.getProductById(id);
    }
}
