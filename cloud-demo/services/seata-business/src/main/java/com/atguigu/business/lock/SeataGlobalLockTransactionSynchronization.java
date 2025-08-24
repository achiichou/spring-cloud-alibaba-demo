package com.atguigu.business.lock;

import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
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
 * Seata全局事務與分布式鎖同步器
 * 
 * 實現分布式鎖與Seata全局事務生命週期的同步，確保：
 * 1. 全局事務提交後釋放鎖
 * 2. 全局事務回滾後立即釋放鎖
 * 3. 事務超時時配合鎖超時機制正確釋放
 * 4. 分布式鎖與全局事務狀態保持一致
 * 
 * @author system
 */
@Component
public class SeataGlobalLockTransactionSynchronization implements TransactionSynchronization {
    
    private static final Logger logger = LoggerFactory.getLogger(SeataGlobalLockTransactionSynchronization.class);
    
    @Autowired
    private DistributedLock distributedLock;
    
    @Autowired(required = false)
    private LockMonitorService lockMonitorService;
    
    @Autowired(required = false)
    private CrossServiceLockMetricsCollector metricsCollector;
    
    @Value("${spring.application.name:seata-business}")
    private String serviceName;
    
    @Value("${distributed.lock.seata.auto-release:true}")
    private boolean autoReleaseOnTransactionEnd;
    
    @Value("${distributed.lock.seata.release-timeout:5000}")
    private long releaseTimeoutMs;
    
    // 存儲當前事務持有的鎖信息
    private static final ThreadLocal<ConcurrentMap<String, LockTransactionContext>> transactionLocks = 
            ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    /**
     * 鎖事務上下文
     */
    public static class LockTransactionContext {
        private final String lockKey;
        private final String xid; // Seata全局事務ID
        private final long acquireTime;
        private final String businessContext;
        private volatile boolean released;
        
        public LockTransactionContext(String lockKey, String xid, String businessContext) {
            this.lockKey = lockKey;
            this.xid = xid;
            this.acquireTime = System.currentTimeMillis();
            this.businessContext = businessContext;
            this.released = false;
        }
        
        // Getters
        public String getLockKey() { return lockKey; }
        public String getXid() { return xid; }
        public long getAcquireTime() { return acquireTime; }
        public String getBusinessContext() { return businessContext; }
        public boolean isReleased() { return released; }
        public void setReleased(boolean released) { this.released = released; }
    }
    
    @PostConstruct
    public void init() {
        logger.info("SeataGlobalLockTransactionSynchronization initialized for service: {}", serviceName);
        logger.info("Auto-release on transaction end: {}, Release timeout: {}ms", 
                   autoReleaseOnTransactionEnd, releaseTimeoutMs);
    }
    
    /**
     * 註冊鎖到當前全局事務
     * 
     * @param lockKey 鎖鍵
     * @param businessContext 業務上下文
     */
    public void registerLockToTransaction(String lockKey, String businessContext) {
        if (!autoReleaseOnTransactionEnd) {
            return;
        }
        
        String xid = RootContext.getXID();
        if (xid == null) {
            logger.debug("No global transaction found, skipping lock registration for key: {}", lockKey);
            return;
        }
        
        // 註冊事務同步器
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            if (!TransactionSynchronizationManager.getSynchronizations().contains(this)) {
                TransactionSynchronizationManager.registerSynchronization(this);
                logger.debug("Registered transaction synchronization for XID: {}", xid);
            }
        }
        
        // 記錄鎖與事務的關聯
        LockTransactionContext context = new LockTransactionContext(lockKey, xid, businessContext);
        transactionLocks.get().put(lockKey, context);
        
        logger.info("Registered distributed lock: {} to global transaction: {} in service: {}", 
                   lockKey, xid, serviceName);
        
        // 記錄事務鎖註冊事件
        if (lockMonitorService != null) {
            lockMonitorService.recordTransactionLockEvent(lockKey, xid, serviceName, 
                LockMonitorService.TransactionLockOperation.REGISTER);
        }
    }
    
    /**
     * 從當前事務中移除鎖註冊
     * 
     * @param lockKey 鎖鍵
     */
    public void unregisterLockFromTransaction(String lockKey) {
        LockTransactionContext context = transactionLocks.get().remove(lockKey);
        if (context != null) {
            logger.debug("Unregistered distributed lock: {} from global transaction: {} in service: {}", 
                        lockKey, context.getXid(), serviceName);
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
        String xid = RootContext.getXID();
        logger.debug("Transaction suspended for XID: {} in service: {}", xid, serviceName);
        
        // 事務掛起時，暫時不處理鎖，等待事務恢復
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        if (!locks.isEmpty()) {
            logger.debug("Transaction suspended with {} active locks in service: {}", locks.size(), serviceName);
        }
    }
    
    @Override
    public void resume() {
        String xid = RootContext.getXID();
        logger.debug("Transaction resumed for XID: {} in service: {}", xid, serviceName);
        
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
        String xid = RootContext.getXID();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        if (!locks.isEmpty()) {
            logger.debug("Flushing transaction with {} active locks for XID: {} in service: {}", 
                        locks.size(), xid, serviceName);
            
            // 驗證所有鎖仍然由當前線程持有
            for (LockTransactionContext context : locks.values()) {
                if (!distributedLock.isHeldByCurrentThread(context.getLockKey())) {
                    logger.error("Critical: Lock {} is not held by current thread during flush for XID: {} in service: {}", 
                                context.getLockKey(), xid, serviceName);
                    
                    // 記錄鎖丟失事件
                    if (metricsCollector != null) {
                        metricsCollector.recordLockLost(context.getLockKey(), serviceName, xid);
                    }
                }
            }
        }
    }
    
    @Override
    public void beforeCommit(boolean readOnly) {
        String xid = RootContext.getXID();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        logger.debug("Before commit for XID: {} with {} locks (readOnly: {}) in service: {}", 
                    xid, locks.size(), readOnly, serviceName);
        
        if (!locks.isEmpty()) {
            // 在提交前記錄鎖持有時間
            for (LockTransactionContext context : locks.values()) {
                long holdTime = System.currentTimeMillis() - context.getAcquireTime();
                logger.debug("Lock {} held for {} ms before commit in service: {}", 
                            context.getLockKey(), holdTime, serviceName);
                
                if (metricsCollector != null) {
                    metricsCollector.recordTransactionLockHoldTime(context.getLockKey(), serviceName, 
                        xid, java.time.Duration.ofMillis(holdTime));
                }
            }
        }
    }
    
    @Override
    public void beforeCompletion() {
        String xid = RootContext.getXID();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        logger.debug("Before completion for XID: {} with {} locks in service: {}", 
                    xid, locks.size(), serviceName);
        
        // 在事務完成前，準備釋放鎖
        if (!locks.isEmpty()) {
            logger.info("Preparing to release {} distributed locks for global transaction: {} in service: {}", 
                       locks.size(), xid, serviceName);
        }
    }
    
    @Override
    public void afterCommit() {
        String xid = RootContext.getXID();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        logger.info("Global transaction committed successfully for XID: {} in service: {}", xid, serviceName);
        
        if (!locks.isEmpty()) {
            logger.info("Releasing {} distributed locks after global transaction commit for XID: {} in service: {}", 
                       locks.size(), xid, serviceName);
            
            // 全局事務提交後釋放所有鎖
            releaseTransactionLocks(locks, "COMMIT", xid);
        }
    }
    
    @Override
    public void afterCompletion(int status) {
        String xid = RootContext.getXID();
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        String statusStr = getTransactionStatusString(status);
        logger.info("Global transaction completed with status: {} for XID: {} in service: {}", 
                   statusStr, xid, serviceName);
        
        try {
            if (status == STATUS_ROLLED_BACK) {
                // 全局事務回滾後立即釋放鎖
                if (!locks.isEmpty()) {
                    logger.warn("Releasing {} distributed locks after global transaction rollback for XID: {} in service: {}", 
                               locks.size(), xid, serviceName);
                    
                    releaseTransactionLocks(locks, "ROLLBACK", xid);
                }
            } else if (status == STATUS_COMMITTED) {
                // 提交狀態下，鎖應該已經在afterCommit中釋放
                if (!locks.isEmpty()) {
                    logger.warn("Found {} unreleased locks after commit for XID: {} in service: {}", 
                               locks.size(), xid, serviceName);
                    
                    releaseTransactionLocks(locks, "COMMIT_CLEANUP", xid);
                }
            } else {
                // 其他狀態（如未知狀態）也釋放鎖
                if (!locks.isEmpty()) {
                    logger.warn("Releasing {} distributed locks for unknown transaction status: {} for XID: {} in service: {}", 
                               locks.size(), statusStr, xid, serviceName);
                    
                    releaseTransactionLocks(locks, "UNKNOWN_STATUS", xid);
                }
            }
        } finally {
            // 清理線程本地存儲
            transactionLocks.remove();
            logger.debug("Cleaned up transaction lock context for XID: {} in service: {}", xid, serviceName);
        }
    }
    
    /**
     * 釋放事務相關的所有鎖
     */
    private void releaseTransactionLocks(ConcurrentMap<String, LockTransactionContext> locks, 
                                       String reason, String xid) {
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
                    
                    // 記錄鎖釋放事件
                    if (lockMonitorService != null) {
                        lockMonitorService.recordTransactionLockEvent(context.getLockKey(), xid, serviceName, 
                            LockMonitorService.TransactionLockOperation.RELEASE);
                    }
                    
                } else {
                    logger.warn("Lock {} is not held by current thread, skipping release for reason: {} in service: {}", 
                               context.getLockKey(), reason, serviceName);
                    context.setReleased(true); // 標記為已釋放，避免重複處理
                }
                
            } catch (Exception e) {
                failureCount++;
                logger.error("Failed to release distributed lock: {} for reason: {} in service: {}", 
                            context.getLockKey(), reason, serviceName, e);
                
                // 記錄鎖釋放失敗事件
                if (lockMonitorService != null) {
                    lockMonitorService.recordTransactionLockEvent(context.getLockKey(), xid, serviceName, 
                        LockMonitorService.TransactionLockOperation.RELEASE_FAILED);
                }
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("Released {} locks successfully, {} failed in {} ms for reason: {} and XID: {} in service: {}", 
                   successCount, failureCount, totalTime, reason, xid, serviceName);
        
        // 記錄批量釋放指標
        if (metricsCollector != null) {
            metricsCollector.recordBatchLockRelease(serviceName, xid, reason, 
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
     * 檢查當前是否在全局事務中
     */
    public boolean isInGlobalTransaction() {
        return RootContext.getXID() != null;
    }
    
    /**
     * 獲取當前全局事務ID
     */
    public String getCurrentGlobalTransactionId() {
        return RootContext.getXID();
    }
    
    /**
     * 強制釋放指定事務的所有鎖（管理功能）
     */
    public int forceReleaseTransactionLocks(String xid) {
        logger.warn("Force releasing all locks for global transaction: {} in service: {}", xid, serviceName);
        
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        int releasedCount = 0;
        
        for (LockTransactionContext context : locks.values()) {
            if (xid.equals(context.getXid()) && !context.isReleased()) {
                try {
                    if (distributedLock instanceof RedisDistributedLock) {
                        RedisDistributedLock redisLock = (RedisDistributedLock) distributedLock;
                        if (redisLock.forceUnlock(context.getLockKey())) {
                            context.setReleased(true);
                            releasedCount++;
                            logger.warn("Force released lock: {} for XID: {} in service: {}", 
                                       context.getLockKey(), xid, serviceName);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to force release lock: {} for XID: {} in service: {}", 
                                context.getLockKey(), xid, serviceName, e);
                }
            }
        }
        
        logger.warn("Force released {} locks for global transaction: {} in service: {}", 
                   releasedCount, xid, serviceName);
        
        return releasedCount;
    }
    
    /**
     * 獲取統計信息
     */
    public TransactionLockStatistics getStatistics() {
        ConcurrentMap<String, LockTransactionContext> locks = transactionLocks.get();
        
        return new TransactionLockStatistics(
            locks.size(),
            locks.values().stream().mapToLong(ctx -> System.currentTimeMillis() - ctx.getAcquireTime()).max().orElse(0),
            locks.values().stream().mapToLong(ctx -> System.currentTimeMillis() - ctx.getAcquireTime()).sum() / Math.max(locks.size(), 1),
            RootContext.getXID(),
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
        private final String currentXid;
        private final String serviceName;
        
        public TransactionLockStatistics(int activeLockCount, long maxHoldTime, long averageHoldTime, 
                                       String currentXid, String serviceName) {
            this.activeLockCount = activeLockCount;
            this.maxHoldTime = maxHoldTime;
            this.averageHoldTime = averageHoldTime;
            this.currentXid = currentXid;
            this.serviceName = serviceName;
        }
        
        // Getters
        public int getActiveLockCount() { return activeLockCount; }
        public long getMaxHoldTime() { return maxHoldTime; }
        public long getAverageHoldTime() { return averageHoldTime; }
        public String getCurrentXid() { return currentXid; }
        public String getServiceName() { return serviceName; }
        
        @Override
        public String toString() {
            return String.format("TransactionLockStatistics{activeLocks=%d, maxHoldTime=%dms, avgHoldTime=%dms, xid=%s, service=%s}", 
                               activeLockCount, maxHoldTime, averageHoldTime, currentXid, serviceName);
        }
    }
}