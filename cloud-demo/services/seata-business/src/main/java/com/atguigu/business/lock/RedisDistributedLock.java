package com.atguigu.business.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 基於Redis的分布式鎖實現
 * 使用Redisson客戶端提供跨服務的分布式鎖功能
 */
@Component
public class RedisDistributedLock implements DistributedLock {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLock.class);
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Value("${spring.application.name:seata-business}")
    private String serviceName;
    
    @Value("${distributed.lock.default-wait-time:5}")
    private long defaultWaitTime;
    
    @Value("${distributed.lock.default-lease-time:30}")
    private long defaultLeaseTime;
    
    @Value("${distributed.lock.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${distributed.lock.retry-base-delay:100}")
    private long retryBaseDelay;
    
    @Value("${distributed.lock.enable-degradation:true}")
    private boolean enableDegradation;
    
    @Value("${distributed.lock.circuit-breaker-threshold:5}")
    private int circuitBreakerThreshold;
    
    // 存儲當前線程持有的鎖上下文
    private final ThreadLocal<ConcurrentHashMap<String, CrossServiceLockContext>> lockContextHolder = 
            ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    // 記錄連續失敗次數，用於熔斷器
    private volatile int consecutiveFailures = 0;
    private volatile long lastFailureTime = 0;
    
    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime) {
        // 檢查熔斷器狀態
        if (isCircuitOpen()) {
            if (enableDegradation) {
                logger.warn("Circuit breaker is open, using degradation mode for lock: {} by service: {}", 
                           lockKey, serviceName);
                return tryDegradedLock(lockKey);
            } else {
                logger.error("Circuit breaker is open and degradation is disabled for lock: {} by service: {}", 
                           lockKey, serviceName);
                return false;
            }
        }
        
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                RLock lock = redissonClient.getLock(lockKey);
                boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
                
                if (acquired) {
                    // 記錄鎖上下文信息
                    CrossServiceLockContext context = new CrossServiceLockContext(
                        lockKey, serviceName, "distributed-lock-operation"
                    );
                    lockContextHolder.get().put(lockKey, context);
                    
                    // 重置失敗計數器
                    consecutiveFailures = 0;
                    
                    logger.info("Successfully acquired distributed lock: {} by service: {} with holder: {} (attempt: {})", 
                               lockKey, serviceName, context.getLockHolder(), attempt);
                    return true;
                } else {
                    logger.warn("Failed to acquire distributed lock: {} by service: {} after waiting {} seconds (attempt: {})", 
                               lockKey, serviceName, waitTime, attempt);
                    
                    if (attempt < maxRetryAttempts) {
                        // 指數退避策略
                        long delay = calculateBackoffDelay(attempt);
                        logger.info("Retrying lock acquisition for {} in {} ms (attempt: {}/{})", 
                                   lockKey, delay, attempt, maxRetryAttempts);
                        Thread.sleep(delay);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while trying to acquire lock: {} by service: {} (attempt: {})", 
                           lockKey, serviceName, attempt, e);
                recordFailure();
                return false;
            } catch (Exception e) {
                logger.error("Error occurred while trying to acquire lock: {} by service: {} (attempt: {})", 
                           lockKey, serviceName, attempt, e);
                recordFailure();
                
                if (attempt < maxRetryAttempts) {
                    // 指數退避策略
                    try {
                        long delay = calculateBackoffDelay(attempt);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else if (enableDegradation) {
                    logger.warn("All retry attempts failed, trying degradation mode for lock: {} by service: {}", 
                               lockKey, serviceName);
                    return tryDegradedLock(lockKey);
                }
            }
        }
        
        recordFailure();
        return false;
    }
    
    @Override
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, defaultWaitTime, defaultLeaseTime);
    }
    
    @Override
    public void unlock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            
            // 檢查是否由當前線程持有
            if (lock.isHeldByCurrentThread()) {
                CrossServiceLockContext context = lockContextHolder.get().remove(lockKey);
                lock.unlock();
                
                if (context != null) {
                    logger.info("Successfully released distributed lock: {} by service: {} with holder: {}", 
                               lockKey, serviceName, context.getLockHolder());
                } else {
                    logger.info("Successfully released distributed lock: {} by service: {}", lockKey, serviceName);
                }
            } else {
                logger.warn("Attempted to unlock a lock not held by current thread: {} by service: {}", 
                           lockKey, serviceName);
            }
        } catch (Exception e) {
            logger.error("Error occurred while releasing lock: {} by service: {}", lockKey, serviceName, e);
            // 清理本地上下文，即使釋放失敗
            lockContextHolder.get().remove(lockKey);
        }
    }
    
    @Override
    public boolean isLocked(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.isLocked();
        } catch (Exception e) {
            logger.error("Error occurred while checking lock status: {} by service: {}", lockKey, serviceName, e);
            return false;
        }
    }
    
    @Override
    public boolean isHeldByCurrentThread(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.isHeldByCurrentThread();
        } catch (Exception e) {
            logger.error("Error occurred while checking if lock is held by current thread: {} by service: {}", 
                        lockKey, serviceName, e);
            return false;
        }
    }
    
    @Override
    public long getRemainingTime(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            return lock.remainTimeToLive();
        } catch (Exception e) {
            logger.error("Error occurred while getting remaining time for lock: {} by service: {}", 
                        lockKey, serviceName, e);
            return -2; // 表示鎖不存在或發生錯誤
        }
    }
    
    /**
     * 獲取當前線程持有的鎖上下文
     */
    public CrossServiceLockContext getLockContext(String lockKey) {
        return lockContextHolder.get().get(lockKey);
    }
    
    /**
     * 強制釋放鎖（管理功能）
     * 注意：這個方法會強制釋放鎖，即使不是當前線程持有
     */
    public boolean forceUnlock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            boolean result = lock.forceUnlock();
            
            if (result) {
                logger.warn("Force unlocked distributed lock: {} by service: {}", lockKey, serviceName);
                // 清理本地上下文
                lockContextHolder.get().remove(lockKey);
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error occurred while force unlocking: {} by service: {}", lockKey, serviceName, e);
            return false;
        }
    }
    
    /**
     * 清理當前線程的鎖上下文
     */
    public void clearLockContext() {
        lockContextHolder.get().clear();
    }
    
    /**
     * 獲取服務名稱
     */
    public String getServiceName() {
        return serviceName;
    }
    
    /**
     * 檢查熔斷器是否開啟
     */
    private boolean isCircuitOpen() {
        if (consecutiveFailures >= circuitBreakerThreshold) {
            // 熔斷器開啟30秒後嘗試半開狀態
            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime;
            if (timeSinceLastFailure > Duration.ofSeconds(30).toMillis()) {
                logger.info("Circuit breaker entering half-open state for service: {}", serviceName);
                consecutiveFailures = circuitBreakerThreshold - 1; // 降低閾值，給一次機會
                return false;
            }
            return true;
        }
        return false;
    }
    
    /**
     * 記錄失敗並更新熔斷器狀態
     */
    private void recordFailure() {
        consecutiveFailures++;
        lastFailureTime = System.currentTimeMillis();
        
        if (consecutiveFailures >= circuitBreakerThreshold) {
            logger.error("Circuit breaker opened for service: {} after {} consecutive failures", 
                        serviceName, consecutiveFailures);
        }
    }
    
    /**
     * 計算指數退避延遲時間
     */
    private long calculateBackoffDelay(int attempt) {
        // 指數退避：base * (2^attempt) + 隨機抖動
        long exponentialDelay = retryBaseDelay * (1L << attempt);
        long jitter = ThreadLocalRandom.current().nextLong(retryBaseDelay / 2);
        return Math.min(exponentialDelay + jitter, 5000); // 最大延遲5秒
    }
    
    /**
     * 降級模式鎖實現
     * 當Redis不可用時，使用本地鎖作為降級方案
     */
    private boolean tryDegradedLock(String lockKey) {
        logger.warn("Using degraded lock mode for key: {} by service: {}", lockKey, serviceName);
        
        // 簡單的降級策略：使用本地鎖和短暫的等待
        // 注意：這只能保證本服務實例內的互斥，不能保證跨服務互斥
        synchronized (this) {
            try {
                // 記錄降級鎖上下文
                CrossServiceLockContext context = new CrossServiceLockContext(
                    lockKey + ":degraded", serviceName, "degraded-lock-operation"
                );
                lockContextHolder.get().put(lockKey, context);
                
                logger.warn("Acquired degraded lock (local only): {} by service: {}", lockKey, serviceName);
                return true;
            } catch (Exception e) {
                logger.error("Failed to acquire even degraded lock: {} by service: {}", lockKey, serviceName, e);
                return false;
            }
        }
    }
    
    /**
     * 健康檢查：檢查Redis連接狀態
     */
    public boolean isHealthy() {
        try {
            redissonClient.getBucket("health-check").isExists();
            return true;
        } catch (Exception e) {
            logger.warn("Redis health check failed for service: {}", serviceName, e);
            return false;
        }
    }
    
    /**
     * 獲取熔斷器狀態信息
     */
    public CircuitBreakerStatus getCircuitBreakerStatus() {
        return new CircuitBreakerStatus(
            consecutiveFailures,
            circuitBreakerThreshold,
            isCircuitOpen(),
            lastFailureTime
        );
    }
    
    /**
     * 重置熔斷器狀態
     */
    public void resetCircuitBreaker() {
        consecutiveFailures = 0;
        lastFailureTime = 0;
        logger.info("Circuit breaker reset for service: {}", serviceName);
    }
    
    /**
     * 熔斷器狀態信息類
     */
    public static class CircuitBreakerStatus {
        private final int consecutiveFailures;
        private final int threshold;
        private final boolean isOpen;
        private final long lastFailureTime;
        
        public CircuitBreakerStatus(int consecutiveFailures, int threshold, boolean isOpen, long lastFailureTime) {
            this.consecutiveFailures = consecutiveFailures;
            this.threshold = threshold;
            this.isOpen = isOpen;
            this.lastFailureTime = lastFailureTime;
        }
        
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public int getThreshold() { return threshold; }
        public boolean isOpen() { return isOpen; }
        public long getLastFailureTime() { return lastFailureTime; }
    }
}