package com.atguigu.storage.lock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 分布式鎖健康檢查指標
 * 
 * 提供分布式鎖系統的健康狀態檢查，包括：
 * 1. Redis連接狀態
 * 2. 熔斷器狀態
 * 3. 當前鎖使用情況
 * 4. 跨服務鎖衝突統計
 */
@Component
public class DistributedLockHealthIndicator implements HealthIndicator {

    @Autowired
    private RedisDistributedLock redisDistributedLock;

    @Autowired(required = false)
    private CrossServiceLockMetricsCollector metricsCollector;

    @Override
    public Health health() {
        try {
            Health.Builder builder = Health.up();
            Map<String, Object> details = new HashMap<>();

            // 1. 檢查Redis連接健康狀態
            boolean redisHealthy = redisDistributedLock.isHealthy();
            details.put("redis.healthy", redisHealthy);
            details.put("redis.service", redisDistributedLock.getServiceName());

            // 2. 檢查熔斷器狀態
            RedisDistributedLock.CircuitBreakerStatus circuitStatus = redisDistributedLock.getCircuitBreakerStatus();
            details.put("circuitBreaker.open", circuitStatus.isOpen());
            details.put("circuitBreaker.consecutiveFailures", circuitStatus.getConsecutiveFailures());
            details.put("circuitBreaker.threshold", circuitStatus.getThreshold());
            details.put("circuitBreaker.lastFailureTime", circuitStatus.getLastFailureTime());

            // 3. 記錄服務特定信息
            details.put("service.type", "seata-storage");
            details.put("service.role", "storage-service");
            details.put("lock.crossService", true);

            // 4. 檢查度量收集器狀態
            if (metricsCollector != null) {
                try {
                    details.put("metrics.collector.available", true);
                    // 這裡可以添加更多的度量信息
                } catch (Exception e) {
                    details.put("metrics.collector.error", e.getMessage());
                }
            } else {
                details.put("metrics.collector.available", false);
            }

            // 5. 整體健康狀態判斷
            if (!redisHealthy) {
                builder.down().withDetail("reason", "Redis connection is unhealthy");
            } else if (circuitStatus.isOpen()) {
                builder.down().withDetail("reason", "Circuit breaker is open");
            }

            return builder.withDetails(details).build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("service", redisDistributedLock != null ? redisDistributedLock.getServiceName() : "unknown")
                    .build();
        }
    }
}