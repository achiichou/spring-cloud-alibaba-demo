package com.atguigu.business.lock;

import java.util.List;
import java.util.Map;

/**
 * 跨服務分布式鎖監控服務接口
 * 提供鎖信息查詢、統計數據收集、管理功能和衝突檢測
 * 
 * @author Kiro
 */
public interface LockMonitorService {
    
    /**
     * 獲取當前所有鎖信息，區分服務來源
     * 
     * @return 所有鎖信息列表
     */
    List<LockInfo> getAllLocks();
    
    /**
     * 獲取指定鎖信息
     * 
     * @param lockKey 鎖鍵
     * @return 鎖信息，如果鎖不存在則返回null
     */
    LockInfo getLockInfo(String lockKey);
    
    /**
     * 根據服務來源獲取鎖信息
     * 
     * @param serviceSource 服務來源 (seata-business, seata-storage)
     * @return 指定服務的鎖信息列表
     */
    List<LockInfo> getLocksByService(String serviceSource);
    
    /**
     * 強制釋放鎖（管理功能）
     * 
     * @param lockKey 鎖鍵
     * @return 是否成功釋放
     */
    boolean forceUnlock(String lockKey);
    
    /**
     * 批量強制釋放鎖
     * 
     * @param lockKeys 鎖鍵列表
     * @return 成功釋放的鎖鍵列表
     */
    List<String> batchForceUnlock(List<String> lockKeys);
    
    /**
     * 獲取跨服務鎖統計信息
     * 
     * @return 鎖統計數據
     */
    LockStatistics getLockStatistics();
    
    /**
     * 重置統計信息
     */
    void resetStatistics();
    
    /**
     * 檢測跨服務鎖衝突
     * 
     * @return 衝突信息映射，Key為鎖鍵，Value為衝突詳情
     */
    Map<String, LockConflictInfo> detectCrossServiceConflicts();
    
    /**
     * 獲取指定時間範圍內的鎖統計
     * 
     * @param startTimeMillis 開始時間（毫秒時間戳）
     * @param endTimeMillis 結束時間（毫秒時間戳）
     * @return 時間範圍內的統計數據
     */
    LockStatistics getLockStatistics(long startTimeMillis, long endTimeMillis);
    
    /**
     * 獲取活躍鎖數量
     * 
     * @return 當前活躍的鎖數量
     */
    int getActiveLockCount();
    
    /**
     * 獲取各服務的鎖使用情況
     * 
     * @return 服務鎖使用統計映射
     */
    Map<String, ServiceLockUsage> getServiceLockUsage();
    
    /**
     * 檢查鎖是否存在潛在的死鎖風險
     * 
     * @return 死鎖風險檢測結果
     */
    List<DeadlockRiskInfo> detectDeadlockRisk();
    
    /**
     * 獲取鎖持有時間過長的警告信息
     * 
     * @param thresholdSeconds 閾值時間（秒）
     * @return 持有時間過長的鎖信息
     */
    List<LockInfo> getLongHeldLocks(long thresholdSeconds);
    
    /**
     * 記錄鎖操作事件（用於統計）
     * 
     * @param lockKey 鎖鍵
     * @param serviceSource 服務來源
     * @param operation 操作類型 (ACQUIRE, RELEASE, TIMEOUT, CONFLICT)
     * @param success 是否成功
     * @param duration 操作耗時（毫秒）
     */
    void recordLockEvent(String lockKey, String serviceSource, LockOperation operation, 
                        boolean success, long duration);
    
    /**
     * 記錄事務鎖事件（用於Seata事務集成統計）
     * 
     * @param lockKey 鎖鍵
     * @param xid 全局事務ID
     * @param serviceSource 服務來源
     * @param operation 事務鎖操作類型
     */
    void recordTransactionLockEvent(String lockKey, String xid, String serviceSource, 
                                   TransactionLockOperation operation);
    
    /**
     * 鎖操作類型枚舉
     */
    enum LockOperation {
        ACQUIRE,    // 獲取鎖
        RELEASE,    // 釋放鎖
        TIMEOUT,    // 超時
        CONFLICT,   // 衝突
        FORCE_UNLOCK // 強制釋放
    }
    
    /**
     * 事務鎖操作類型枚舉
     */
    enum TransactionLockOperation {
        REGISTER,       // 註冊到事務
        RELEASE,        // 事務結束時釋放
        RELEASE_FAILED, // 釋放失敗
        FORCE_RELEASE   // 強制釋放
    }
    
    /**
     * 鎖衝突信息
     */
    class LockConflictInfo {
        private String lockKey;
        private String currentHolder;
        private String currentHolderService;
        private List<String> waitingServices;
        private long conflictStartTime;
        private int conflictCount;
        
        public LockConflictInfo(String lockKey, String currentHolder, String currentHolderService) {
            this.lockKey = lockKey;
            this.currentHolder = currentHolder;
            this.currentHolderService = currentHolderService;
            this.conflictStartTime = System.currentTimeMillis();
        }
        
        // Getters and Setters
        public String getLockKey() { return lockKey; }
        public void setLockKey(String lockKey) { this.lockKey = lockKey; }
        
        public String getCurrentHolder() { return currentHolder; }
        public void setCurrentHolder(String currentHolder) { this.currentHolder = currentHolder; }
        
        public String getCurrentHolderService() { return currentHolderService; }
        public void setCurrentHolderService(String currentHolderService) { this.currentHolderService = currentHolderService; }
        
        public List<String> getWaitingServices() { return waitingServices; }
        public void setWaitingServices(List<String> waitingServices) { this.waitingServices = waitingServices; }
        
        public long getConflictStartTime() { return conflictStartTime; }
        public void setConflictStartTime(long conflictStartTime) { this.conflictStartTime = conflictStartTime; }
        
        public int getConflictCount() { return conflictCount; }
        public void setConflictCount(int conflictCount) { this.conflictCount = conflictCount; }
        
        public void incrementConflictCount() { this.conflictCount++; }
    }
    
    /**
     * 服務鎖使用情況
     */
    class ServiceLockUsage {
        private String serviceName;
        private int activeLocks;
        private long totalLockRequests;
        private long successfulLocks;
        private double successRate;
        private long averageHoldTime;
        
        public ServiceLockUsage(String serviceName) {
            this.serviceName = serviceName;
        }
        
        // Getters and Setters
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        
        public int getActiveLocks() { return activeLocks; }
        public void setActiveLocks(int activeLocks) { this.activeLocks = activeLocks; }
        
        public long getTotalLockRequests() { return totalLockRequests; }
        public void setTotalLockRequests(long totalLockRequests) { this.totalLockRequests = totalLockRequests; }
        
        public long getSuccessfulLocks() { return successfulLocks; }
        public void setSuccessfulLocks(long successfulLocks) { this.successfulLocks = successfulLocks; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        
        public long getAverageHoldTime() { return averageHoldTime; }
        public void setAverageHoldTime(long averageHoldTime) { this.averageHoldTime = averageHoldTime; }
    }
    
    /**
     * 死鎖風險信息
     */
    class DeadlockRiskInfo {
        private String lockKey;
        private String holderService;
        private List<String> waitingServices;
        private long riskScore;
        private String riskDescription;
        
        public DeadlockRiskInfo(String lockKey, String holderService) {
            this.lockKey = lockKey;
            this.holderService = holderService;
        }
        
        // Getters and Setters
        public String getLockKey() { return lockKey; }
        public void setLockKey(String lockKey) { this.lockKey = lockKey; }
        
        public String getHolderService() { return holderService; }
        public void setHolderService(String holderService) { this.holderService = holderService; }
        
        public List<String> getWaitingServices() { return waitingServices; }
        public void setWaitingServices(List<String> waitingServices) { this.waitingServices = waitingServices; }
        
        public long getRiskScore() { return riskScore; }
        public void setRiskScore(long riskScore) { this.riskScore = riskScore; }
        
        public String getRiskDescription() { return riskDescription; }
        public void setRiskDescription(String riskDescription) { this.riskDescription = riskDescription; }
    }
}