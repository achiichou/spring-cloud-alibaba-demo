package com.atguigu.business.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

import com.atguigu.business.lock.LockMonitorService;
import com.atguigu.business.lock.RedisDistributedLock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 分布式鎖監控配置
 * 
 * 配置分布式鎖系統的監控和告警功能，包括：
 * 1. 自定義Actuator端點
 * 2. JMX監控Bean
 * 3. 度量指標配置
 * 4. 告警閾值設定
 */
@Configuration
public class DistributedLockMonitoringConfiguration {

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * 分布式鎖自定義端點
     */
    @Endpoint(id = "distributed-lock")
    @ManagedResource(objectName = "com.atguigu.business:type=DistributedLock,name=LockEndpoint")
    public static class DistributedLockEndpoint {

        @Autowired
        private RedisDistributedLock redisDistributedLock;

        @Autowired
        private LockMonitorService lockMonitorService;

        @ReadOperation
        @ManagedOperation(description = "Get comprehensive distributed lock status")
        public Map<String, Object> lockStatus() {
            Map<String, Object> status = new HashMap<>();
            
            try {
                // 基本狀態信息
                status.put("service", redisDistributedLock.getServiceName());
                status.put("healthy", redisDistributedLock.isHealthy());
                status.put("timestamp", System.currentTimeMillis());
                
                // 熔斷器狀態
                RedisDistributedLock.CircuitBreakerStatus circuitStatus = redisDistributedLock.getCircuitBreakerStatus();
                Map<String, Object> circuitInfo = new HashMap<>();
                circuitInfo.put("isOpen", circuitStatus.isOpen());
                circuitInfo.put("consecutiveFailures", circuitStatus.getConsecutiveFailures());
                circuitInfo.put("threshold", circuitStatus.getThreshold());
                circuitInfo.put("lastFailureTime", circuitStatus.getLastFailureTime());
                status.put("circuitBreaker", circuitInfo);
                
                // 當前鎖信息
                status.put("currentLocks", lockMonitorService.getAllLocks());
                
                // 統計信息
                status.put("statistics", lockMonitorService.getLockStatistics());
                
                status.put("status", "OK");
            } catch (Exception e) {
                status.put("status", "ERROR");
                status.put("error", e.getMessage());
            }
            
            return status;
        }

        @ManagedOperation(description = "Reset circuit breaker")
        public String resetCircuitBreaker() {
            try {
                redisDistributedLock.resetCircuitBreaker();
                return "Circuit breaker reset successfully";
            } catch (Exception e) {
                return "Failed to reset circuit breaker: " + e.getMessage();
            }
        }

        @ManagedAttribute(description = "Check if Redis is healthy")
        public boolean isRedisHealthy() {
            return redisDistributedLock.isHealthy();
        }

        @ManagedAttribute(description = "Get service name")
        public String getServiceName() {
            return redisDistributedLock.getServiceName();
        }

        @ManagedAttribute(description = "Get current lock count")
        public int getCurrentLockCount() {
            try {
                return lockMonitorService.getAllLocks().size();
            } catch (Exception e) {
                return -1;
            }
        }
    }

    @Bean
    public DistributedLockEndpoint distributedLockEndpoint() {
        return new DistributedLockEndpoint();
    }

    /**
     * 配置自定義度量指標
     */
    @Bean
    public DistributedLockMetrics distributedLockMetrics(RedisDistributedLock redisDistributedLock,
                                                       LockMonitorService lockMonitorService) {
        return new DistributedLockMetrics(meterRegistry, redisDistributedLock, lockMonitorService);
    }

    /**
     * 分布式鎖度量收集器
     */
    public static class DistributedLockMetrics {
        
        private final MeterRegistry meterRegistry;
        private final RedisDistributedLock redisDistributedLock;
        private final LockMonitorService lockMonitorService;
        
        private final Counter lockAcquisitionAttempts;
        private final Counter lockAcquisitionSuccess;
        private final Counter lockAcquisitionFailures;
        private final Counter circuitBreakerTriggers;
        private final Timer lockHoldTime;
        
        public DistributedLockMetrics(MeterRegistry meterRegistry,
                                    RedisDistributedLock redisDistributedLock,
                                    LockMonitorService lockMonitorService) {
            this.meterRegistry = meterRegistry;
            this.redisDistributedLock = redisDistributedLock;
            this.lockMonitorService = lockMonitorService;
            
            // 初始化計數器
            this.lockAcquisitionAttempts = Counter.builder("distributed.lock.acquisition.attempts")
                    .tag("service", redisDistributedLock.getServiceName())
                    .description("Total number of lock acquisition attempts")
                    .register(meterRegistry);
                    
            this.lockAcquisitionSuccess = Counter.builder("distributed.lock.acquisition.success")
                    .tag("service", redisDistributedLock.getServiceName())
                    .description("Number of successful lock acquisitions")
                    .register(meterRegistry);
                    
            this.lockAcquisitionFailures = Counter.builder("distributed.lock.acquisition.failures")
                    .tag("service", redisDistributedLock.getServiceName())
                    .description("Number of failed lock acquisitions")
                    .register(meterRegistry);
                    
            this.circuitBreakerTriggers = Counter.builder("distributed.lock.circuit.breaker.triggers")
                    .tag("service", redisDistributedLock.getServiceName())
                    .description("Number of times circuit breaker was triggered")
                    .register(meterRegistry);
                    
            this.lockHoldTime = Timer.builder("distributed.lock.hold.time")
                    .tag("service", redisDistributedLock.getServiceName())
                    .description("Time locks are held")
                    .register(meterRegistry);
            
            // 註冊儀表盤指標
            Gauge.builder("distributed.lock.current.count", () -> {
                try {
                    return this.lockMonitorService.getAllLocks().size();
                } catch (Exception e) {
                    // 記錄異常但回傳 0
                    // log.warn("Failed to get lock count", e);
                    return 0;
                }
            })
            .tag("service", redisDistributedLock.getServiceName())
            .description("Current number of active locks")
            .register(meterRegistry);
                    
            Gauge.builder("distributed.lock.redis.healthy", () -> 
                    this.redisDistributedLock.isHealthy() ? 1 : 0)
                    .tag("service", redisDistributedLock.getServiceName())
                    .description("Redis health status (1 = healthy, 0 = unhealthy)")
                    .register(meterRegistry);;
                        
            Gauge.builder("distributed.lock.circuit.breaker.open", () ->
                    this.redisDistributedLock.getCircuitBreakerStatus().isOpen() ? 1 : 0)
                    .tag("service", redisDistributedLock.getServiceName())
                    .description("Circuit breaker status (1 = open, 0 = closed)")
                    .register(meterRegistry);
        }
        
        public void recordLockAcquisitionAttempt() {
            lockAcquisitionAttempts.increment();
        }
        
        public void recordLockAcquisitionSuccess() {
            lockAcquisitionSuccess.increment();
        }
        
        public void recordLockAcquisitionFailure() {
            lockAcquisitionFailures.increment();
        }
        
        public void recordCircuitBreakerTrigger() {
            circuitBreakerTriggers.increment();
        }
        
        public Timer.Sample startLockHoldTimer() {
            return Timer.start(meterRegistry);
        }
        
        public void recordLockHoldTime(Timer.Sample sample) {
            sample.stop(lockHoldTime);
        }
    }
}