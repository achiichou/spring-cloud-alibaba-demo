package com.atguigu.business.lock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

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

    @Autowired
    private LockMonitorService lockMonitorService;

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

            // 3. 檢查當前鎖使用情況
            try {
                List<LockInfo> currentLocks = lockMonitorService.getAllLocks();
                details.put("locks.current.count", currentLocks.size());

                // 統計各服務的鎖數量
                Map<String, Integer> locksByService = new HashMap<>();
                for (LockInfo lock : currentLocks) {
                    String serviceSource = extractServiceFromHolder(lock.getHolder());
                    locksByService.merge(serviceSource, 1, Integer::sum);
                }
                details.put("locks.current.byService", locksByService);

                // 檢查是否有長期持有的鎖（超過5分鐘）
                java.time.LocalDateTime currentTime = java.time.LocalDateTime.now();
                java.time.Duration longRunningThreshold = java.time.Duration.ofMinutes(5);
                int longRunningLocks = 0;

                for (LockInfo lock : currentLocks) {
                    java.time.Duration lockDuration = java.time.Duration.between(lock.getAcquireTime(), currentTime);
                    if (lockDuration.compareTo(longRunningThreshold) > 0) {
                        longRunningLocks++;
                    }
                }
                details.put("locks.longRunning.count", longRunningLocks);

                if (longRunningLocks > 0) {
                    builder.withDetail("warning", "Found " + longRunningLocks + " long-running locks");
                }
            } catch (Exception e) {
                details.put("locks.monitor.error", e.getMessage());
                builder.withDetail("warning", "Failed to retrieve lock information: " + e.getMessage());
            }

            // 4. 檢查鎖統計信息
            try {
                LockStatistics stats = lockMonitorService.getLockStatistics();
                details.put("statistics.totalRequests", stats.getTotalLockRequests());
                details.put("statistics.successfulLocks", stats.getSuccessfulLocks());
                details.put("statistics.failedLocks", stats.getFailedLocks());

                // 計算成功率
                if (stats.getTotalLockRequests() > 0) {
                    double successRate = (double) stats.getSuccessfulLocks() / stats.getTotalLockRequests() * 100;
                    details.put("statistics.successRate", Math.round(successRate * 100.0) / 100.0);

                    // 如果成功率低於90%，標記為警告
                    if (successRate < 90.0) {
                        builder.withDetail("warning", "Lock success rate is low: " + successRate + "%");
                    }
                } else {
                    details.put("statistics.successRate", 100.0);
                }

                details.put("statistics.averageWaitTime", stats.getAverageWaitTime());
                details.put("statistics.averageHoldTime", stats.getAverageHoldTime());
            } catch (Exception e) {
                details.put("statistics.error", e.getMessage());
                builder.withDetail("warning", "Failed to retrieve lock statistics: " + e.getMessage());
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
                    .withDetail("service",
                            redisDistributedLock != null ? redisDistributedLock.getServiceName() : "unknown")
                    .build();
        }
    }

    /**
     * 從鎖持有者信息中提取服務名稱
     */
    private String extractServiceFromHolder(String holder) {
        if (holder == null) {
            return "unknown";
        }

        // 假設holder格式為: serviceName-instanceId-threadId
        String[] parts = holder.split("-");
        if (parts.length >= 2) {
            return parts[0] + "-" + parts[1]; // serviceName-instanceId
        }
        return holder;
    }
}