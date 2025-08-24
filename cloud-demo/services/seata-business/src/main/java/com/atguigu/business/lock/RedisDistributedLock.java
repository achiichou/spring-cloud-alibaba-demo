package com.atguigu.business.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
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
    
    // 存儲當前線程持有的鎖上下文
    private final ThreadLocal<ConcurrentHashMap<String, CrossServiceLockContext>> lockContextHolder = 
            ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            
            if (acquired) {
                // 記錄鎖上下文信息
                CrossServiceLockContext context = new CrossServiceLockContext(
                    lockKey, serviceName, "distributed-lock-operation"
                );
                lockContextHolder.get().put(lockKey, context);
                
                logger.info("Successfully acquired distributed lock: {} by service: {} with holder: {}", 
                           lockKey, serviceName, context.getLockHolder());
            } else {
                logger.warn("Failed to acquire distributed lock: {} by service: {} after waiting {} seconds", 
                           lockKey, serviceName, waitTime);
            }
            
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while trying to acquire lock: {} by service: {}", lockKey, serviceName, e);
            return false;
        } catch (Exception e) {
            logger.error("Error occurred while trying to acquire lock: {} by service: {}", lockKey, serviceName, e);
            return false;
        }
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
}