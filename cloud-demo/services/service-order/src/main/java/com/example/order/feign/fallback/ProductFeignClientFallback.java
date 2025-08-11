package com.example.order.feign.fallback;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.example.order.feign.ProductFeignClient;
import com.example.product.Product;

import lombok.extern.slf4j.Slf4j;

/**
 * 自訂 fallback 類別，當調用 product 服務失敗時，會調用這個類別中的方法
 * 需要搭配 sentinel 使用
 */
@Slf4j
@Component
public class ProductFeignClientFallback implements ProductFeignClient {

    @Override
    public Product getProductById(Long id) {
        log.error("getProductById 錯誤 fallback, id: {}", id);
        return new Product(id, "Product not found", new BigDecimal(0), 0);
    }
}
