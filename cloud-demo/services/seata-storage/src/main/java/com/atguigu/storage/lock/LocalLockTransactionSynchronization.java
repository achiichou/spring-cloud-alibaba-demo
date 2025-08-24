package com.atguigu.storage.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 本地事務與分布式鎖同步器
 * 
 * 實現分布式鎖與本地事務生命週期的同步，確保：
 * 1. 本地事務提交後釋放鎖
 * 2. 本地事務回滾後立即釋放鎖
 * 3. 確保分布式鎖與本地事務生命週期同步
 * 4. 分布式鎖與本地事務狀態保持一致
 * 
 * @author system
 */
@Component
public class LocalLockTransactionSynchronization implements TransactionSynchronization {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalLockTransactionSynchronization.class);
    
    @Autowired
    private DistributedLock distributedLock;
    
    @Autowired(required = false)
    private CrossServiceLockMetricsCollector metricsCollector;
    
    @Value("${spring.application.name:seata-storage}")
    private String serviceName;
    
    @Value("${distributed.lock.local.auto-release:true}")
    private boolean autoReleaseOnTransactionEnd;
    
    @Value("${distributed.lock.local.release-timeout:5000}")
    private long releaseTimeoutMs;
    
    // 存儲當前事務持有的鎖信息
    private static final ThreadLocal<ConcurrentMap<String, LockTransactionContext>> transactionLocks = 
            ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    /**
     * 鎖事務上下文
     */
    public static class LockTransactionContext {
        private final String lockKey;
        private final String transactionName; // 本地事務名稱
        private final long acquireTime;
        private final String businessContext;
        private volatile boolean released;
        
        public LockTransactionContext(String lockKey, String transactionName, String businessContext) {
            this.lockKey = lockKey;
            this.transactionName = transactionName;
            this.acquireTime = System.currentTimeMillis();
            this.businessContext = businessContext;
            this.released = false;
        }
        
        // Getters
        public String getLockKey() { return lockKey; }
        public String getTransactionName() { return transactionName; }
        public long getAcquireTime() { return acquireTime; }
        public String getBusinessContext() { return businessContext; }
        public boolean isReleased() { return released; }
        public void setReleased(boolean released) { this.released = released; }
    }
    
    @PostConstruct
    public void init() {
        logger.info("LocalLockTransactionSynchronization initialized for service: {}", serviceName);
        logger.info("Auto-release on transaction end: {}, Release timeout: {}ms", 
                   autoReleaseOnTransactionEnd, releaseTimeoutMs);
    }
    
    /**
     * 註冊鎖到當前本地事務
     * 
     * @param lockKey 鎖鍵
     * @param businessContext 業務上下文
     */
    public void registerLockToTransaction(String lockKey, String businessContext) {
        if (!autoReleaseOnTransactionEnd) {
            return;
        }
        
        // 檢查是否在事務中
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            logger.debug("No active transaction found, skipping lock registration for key: {}", lockKey);
            return;
        }
        
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        if (transactionName == null) {
            transactionName = "local-transaction-" + Thread.currentThread().getId();
        }
        
        // 註冊事務同步器
        if (!TransactionSynchronizationManager.getSynchronizations().contains(this)) {
            TransactionSynchronizationManager.registerSynchronization(this);
            logger.debug("Registered transaction synchronization for transaction: {}", transactionName);
        }
        
        // 記錄鎖與事務的關聯
        LockTransactionContext context = new LockTransactionContext(lockKey, transactionName, businessContext);
        transactionLocks.get().put(lockKey, context);
        
        logger.info("Registered distributed lock: {} to local transaction: {} in service: {}", 
                   lockKey, transactionName, serviceName);
    }
    
    /**
     * 從當前事務中移除鎖註冊
     * 
     * @param lockKey 鎖鍵
     */
    public void unregisterLockFromTransaction(String lockKey) {
        LockTransactionContext context = transactionLocks.get().remove(lockKey);
        if (context != null) {
            logger.debug("Unregistered distributed lock: {} from local transaction: {} in service: {}", 
                        lockKey, context.getTransactionName(), serviceName);
        }
    }
    
    /**
     * 檢查鎖是否已註冊到當前事務
     */
    public boolean isLockRegisteredToTransaction(String lockKey) {
        return transactionLocks.get().containsKey(lockKey);
    }
    
    /**
     * 獲取當前事務持有的所有鎖
     */
    public ConcurrentMap<String, LockTransactionContext> getCurrentTransactionLocks() {
        return new ConcurrentHashMap<>(transactionLocks.get());
    }
    
    @Override
    public void suspend() {
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        logger.debug("Transaction suspended for transaction: {} in service: {}", transactionName, serviceName);
        
        // 事務掛起時，暫時不處理鎖，等待事務恢復
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        if (!locks.isEmpty()) {
            logger.debug("Transaction suspended with {} active locks in service: {}", locks.size(), serviceName);
        }
    }
    
    @Override
    public void resume() {
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        logger.debug("Transaction resumed for transaction: {} in service: {}", transactionName, serviceName);
        
        // 事務恢復時，檢查鎖狀態
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        if (!locks.isEmpty()) {
            logger.debug("Transaction resumed with {} active locks in service: {}", locks.size(), serviceName);
            
            // 檢查鎖是否仍然有效
            for (LockTransactionContext context : locks.values()) {
                if (!distributedLock.isHeldByCurrentThread(context.getLockKey())) {
                    logger.warn("Lock {} is no longer held by current thread after transaction resume in service: {}", 
                               context.getLockKey(), serviceName);
                }
            }
        }
    }
    
    @Override
    public void flush() {
        // 在事務提交前的flush階段，確保所有鎖仍然有效
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        if (!locks.isEmpty()) {
            logger.debug("Flushing transaction with {} active locks for transaction: {} in service: {}", 
                        locks.size(), transactionName, serviceName);
            
            // 驗證所有鎖仍然由當前線程持有
            for (LockTransactionContext context : locks.values()) {
                if (!distributedLock.isHeldByCurrentThread(context.getLockKey())) {
                    logger.error("Critical: Lock {} is not held by current thread during flush for transaction: {} in service: {}", 
                                context.getLockKey(), transactionName, serviceName);
                    
                    // 記錄鎖丟失事件
                    if (metricsCollector != null) {
                        metricsCollector.recordLockLost(context.getLockKey(), serviceName, transactionName);
                    }
                }
            }
        }
    }
    
    @Override
    public void beforeCommit(boolean readOnly) {
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        logger.debug("Before commit for transaction: {} with {} locks (readOnly: {}) in service: {}", 
                    transactionName, locks.size(), readOnly, serviceName);
        
        if (!locks.isEmpty()) {
            // 在提交前記錄鎖持有時間
            for (LockTransactionContext context : locks.values()) {
                long holdTime = System.currentTimeMillis() - context.getAcquireTime();
                logger.debug("Lock {} held for {} ms before commit in service: {}", 
                            context.getLockKey(), holdTime, serviceName);
                
                if (metricsCollector != null) {
                    metricsCollector.recordTransactionLockHoldTime(context.getLockKey(), serviceName, 
                        transactionName, java.time.Duration.ofMillis(holdTime));
                }
            }
        }
    }
    
    @Override
    public void beforeCompletion() {
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        logger.debug("Before completion for transaction: {} with {} locks in service: {}", 
                    transactionName, locks.size(), serviceName);
        
        // 在事務完成前，準備釋放鎖
        if (!locks.isEmpty()) {
            logger.info("Preparing to release {} distributed locks for local transaction: {} in service: {}", 
                       locks.size(), transactionName, serviceName);
        }
    }
    
    @Override
    public void afterCommit() {
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        logger.info("Local transaction committed successfully for transaction: {} in service: {}", 
                   transactionName, serviceName);
        
        if (!locks.isEmpty()) {
            logger.info("Releasing {} distributed locks after local transaction commit for transaction: {} in service: {}", 
                       locks.size(), transactionName, serviceName);
            
            // 本地事務提交後釋放所有鎖
            releaseTransactionLocks(locks, "COMMIT", transactionName);
        }
    }
    
    @Override
    public void afterCompletion(int status) {
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        String statusStr = getTransactionStatusString(status);
        logger.info("Local transaction completed with status: {} for transaction: {} in service: {}", 
                   statusStr, transactionName, serviceName);
        
        try {
            if (status == STATUS_ROLLED_BACK) {
                // 本地事務回滾後立即釋放鎖
                if (!locks.isEmpty()) {
                    logger.warn("Releasing {} distributed locks after local transaction rollback for transaction: {} in service: {}", 
                               locks.size(), transactionName, serviceName);
                    
                    releaseTransactionLocks(locks, "ROLLBACK", transactionName);
                }
            } else if (status == STATUS_COMMITTED) {
                // 提交狀態下，鎖應該已經在afterCommit中釋放
                if (!locks.isEmpty()) {
                    logger.warn("Found {} unreleased locks after commit for transaction: {} in service: {}", 
                               locks.size(), transactionName, serviceName);
                    
                    releaseTransactionLocks(locks, "COMMIT_CLEANUP", transactionName);
                }
            } else {
                // 其他狀態（如未知狀態）也釋放鎖
                if (!locks.isEmpty()) {
                    logger.warn("Releasing {} distributed locks for unknown transaction status: {} for transaction: {} in service: {}", 
                               locks.size(), statusStr, transactionName, serviceName);
                    
                    releaseTransactionLocks(locks, "UNKNOWN_STATUS", transactionName);
                }
            }
        } finally {
            // 清理線程本地存儲
            transactionLocks.remove();
            logger.debug("Cleaned up transaction lock context for transaction: {} in service: {}", 
                        transactionName, serviceName);
        }
    }
    
    /**
     * 釋放事務相關的所有鎖
     */
    private void releaseTransactionLocks(ConcurrentMap<String, LockTransactionContext> locks, 
                                       String reason, String transactionName) {
        int successCount = 0;
        int failureCount = 0;
        long startTime = System.currentTimeMillis();
        
        for (LockTransactionContext context : locks.values()) {
            if (context.isReleased()) {
                continue; // 已經釋放的鎖跳過
            }
            
            try {
                // 檢查鎖是否仍然由當前線程持有
                if (distributedLock.isHeldByCurrentThread(context.getLockKey())) {
                    distributedLock.unlock(context.getLockKey());
                    context.setReleased(true);
                    successCount++;
                    
                    long holdTime = System.currentTimeMillis() - context.getAcquireTime();
                    logger.info("Successfully released distributed lock: {} after {} ms for reason: {} in service: {}", 
                               context.getLockKey(), holdTime, reason, serviceName);
                    
                } else {
                    logger.warn("Lock {} is not held by current thread, skipping release for reason: {} in service: {}", 
                               context.getLockKey(), reason, serviceName);
                    context.setReleased(true); // 標記為已釋放，避免重複處理
                }
                
            } catch (Exception e) {
                failureCount++;
                logger.error("Failed to release distributed lock: {} for reason: {} in service: {}", 
                            context.getLockKey(), reason, serviceName, e);
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("Released {} locks successfully, {} failed in {} ms for reason: {} and transaction: {} in service: {}", 
                   successCount, failureCount, totalTime, reason, transactionName, serviceName);
        
        // 記錄批量釋放指標
        if (metricsCollector != null) {
            metricsCollector.recordBatchLockRelease(serviceName, transactionName, reason, 
                successCount, failureCount, java.time.Duration.ofMillis(totalTime));
        }
    }
    
    /**
     * 獲取事務狀態字符串
     */
    private String getTransactionStatusString(int status) {
        switch (status) {
            case STATUS_COMMITTED:
                return "COMMITTED";
            case STATUS_ROLLED_BACK:
                return "ROLLED_BACK";
            case STATUS_UNKNOWN:
                return "UNKNOWN";
            default:
                return "STATUS_" + status;
        }
    }
    
    /**
     * 檢查當前是否在本地事務中
     */
    public boolean isInLocalTransaction() {
        return TransactionSynchronizationManager.isSynchronizationActive();
    }
    
    /**
     * 獲取當前本地事務名稱
     */
    public String getCurrentLocalTransactionName() {
        return TransactionSynchronizationManager.getCurrentTransactionName();
    }
    
    /**
     * 強制釋放指定事務的所有鎖（管理功能）
     */
    public int forceReleaseTransactionLocks(String transactionName) {
        logger.warn("Force releasing all locks for local transaction: {} in service: {}", 
                   transactionName, serviceName);
        
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        int releasedCount = 0;
        
        for (LockTransactionContext context : locks.values()) {
            if (transactionName.equals(context.getTransactionName()) && !context.isReleased()) {
                try {
                    if (distributedLock instanceof RedisDistributedLock) {
                        RedisDistributedLock redisLock = (RedisDistributedLock) distributedLock;
                        if (redisLock.forceUnlock(context.getLockKey())) {
                            context.setReleased(true);
                            releasedCount++;
                            logger.warn("Force released lock: {} for transaction: {} in service: {}", 
                                       context.getLockKey(), transactionName, serviceName);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to force release lock: {} for transaction: {} in service: {}", 
                                context.getLockKey(), transactionName, serviceName, e);
                }
            }
        }
        
        logger.warn("Force released {} locks for local transaction: {} in service: {}", 
                   releasedCount, transactionName, serviceName);
        
        return releasedCount;
    }
    
    /**
     * 獲取統計信息
     */
    public TransactionLockStatistics getStatistics() {
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        String currentTransactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        
        return new TransactionLockStatistics(
            locks.size(),
            locks.values().stream().mapToLong(ctx -> System.currentTimeMillis() - ctx.getAcquireTime()).max().orElse(0),
            locks.values().stream().mapToLong(ctx -> System.currentTimeMillis() - ctx.getAcquireTime()).sum() / Math.max(locks.size(), 1),
            currentTransactionName,
            serviceName
        );
    }
    
    /**
     * 事務鎖統計信息
     */
    public static class TransactionLockStatistics {
        private final int activeLockCount;
        private final long maxHoldTime;
        private final long averageHoldTime;
        private final String currentTransactionName;
        private final String serviceName;
        
        public TransactionLockStatistics(int activeLockCount, long maxHoldTime, long averageHoldTime, 
                                       String currentTransactionName, String serviceName) {
            this.activeLockCount = activeLockCount;
            this.maxHoldTime = maxHoldTime;
            this.averageHoldTime = averageHoldTime;
            this.currentTransactionName = currentTransactionName;
            this.serviceName = serviceName;
        }
        
        // Getters
        public int getActiveLockCount() { return activeLockCount; }
        public long getMaxHoldTime() { return maxHoldTime; }
        public long getAverageHoldTime() { return averageHoldTime; }
        public String getCurrentTransactionName() { return currentTransactionName; }
        public String getServiceName() { return serviceName; }
        
        @Override
        public String toString() {
            return String.format("TransactionLockStatistics{activeLocks=%d, maxHoldTime=%dms, avgHoldTime=%dms, transaction=%s, service=%s}", 
                               activeLockCount, maxHoldTime, averageHoldTime, currentTransactionName, serviceName);
        }
    }
}