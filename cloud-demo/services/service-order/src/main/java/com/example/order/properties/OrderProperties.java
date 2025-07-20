package com.example.order.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "order")  // 不需要加@RefreshScope就可以自動刷新
@Data
public class OrderProperties {

    private String timeout;
    private String autoConfirm;

    private String dbUrl;
}
