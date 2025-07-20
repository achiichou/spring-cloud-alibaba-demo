package com.example.order;

import java.math.BigDecimal;
import java.util.List;

import com.example.product.Product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Order {
    
    private Long id;
    private BigDecimal totalAmount;
    private Long userId;
    private String userName;
    private String address;

    private List<Product> productList;
}
