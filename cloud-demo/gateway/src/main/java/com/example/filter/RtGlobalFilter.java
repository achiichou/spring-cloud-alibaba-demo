package com.example.filter;

import java.time.LocalDateTime;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 全域自訂Filter，所有請求都會經過此Filter
 * 只要實作 GlobalFilter 就會是全域
 */
@Slf4j
@Component
public class RtGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        log.info("請求【{}】開始時間:{} 路徑:{}", exchange.getRequest().getURI(), LocalDateTime.now(), exchange.getRequest().getPath());
        return chain.filter(exchange).doFinally((res) -> {
            long endTime = System.currentTimeMillis();
            log.info("請求【{}】結束時間:{} 路徑:{} 耗時:{}ms", exchange.getRequest().getURI(), LocalDateTime.now(), exchange.getRequest().getPath(), endTime - startTime);
        });
    }

}
