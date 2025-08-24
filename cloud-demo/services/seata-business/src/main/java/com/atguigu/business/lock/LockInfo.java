package com.atguigu.business.lock;

import java.time.LocalDateTime;

/**
 * 鎖信息模型 - 表示分布式鎖的詳細信息，包含服務來源字段
 * 
 * @author Kiro
 */
public class LockInfo {
    
    /**
     * 鎖鍵
     */
    private String lockKey;
    
    /**
     * 鎖持有者標識
     */
    private String holder;
    
    /**
     * 服務來源 (seata-business, seata-storage)
     */
    private String serviceSource;
    
    /**
     * 獲取鎖的時間
     */
    private LocalDateTime acquireTime;
    
    /**
     * 鎖的租約時間（秒）
     */
    private long leaseTime;
    
    /**
     * 剩餘時間（秒）
     */
    private long remainingTime;
    
    /**
     * 業務上下文信息
     */
    private String businessContext;
    
    /**
     * 鎖狀態 (ACTIVE, EXPIRED, RELEASED)
     */
    private LockStatus status;
    
    /**
     * 鎖類型 (STORAGE_DEDUCT, BATCH_OPERATION)
     */
    private String lockType;
    
    public LockInfo() {}
    
    public LockInfo(String lockKey, String holder, String serviceSource) {
        this.lockKey = lockKey;
        this.holder = holder;
        this.serviceSource = serviceSource;
        this.acquireTime = LocalDateTime.now();
        this.status = LockStatus.ACTIVE;
    }
    
    // Getters and Setters
    public String getLockKey() {
        return lockKey;
    }
    
    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }
    
    public String getHolder() {
        return holder;
    }
    
    public void setHolder(String holder) {
        this.holder = holder;
    }
    
    public String getServiceSource() {
        return serviceSource;
    }
    
    public void setServiceSource(String serviceSource) {
        this.serviceSource = serviceSource;
    }
    
    public LocalDateTime getAcquireTime() {
        return acquireTime;
    }
    
    public void setAcquireTime(LocalDateTime acquireTime) {
        this.acquireTime = acquireTime;
    }
    
    public long getLeaseTime() {
        return leaseTime;
    }
    
    public void setLeaseTime(long leaseTime) {
        this.leaseTime = leaseTime;
    }
    
    public long getRemainingTime() {
        return remainingTime;
    }
    
    public void setRemainingTime(long remainingTime) {
        this.remainingTime = remainingTime;
    }
    
    public String getBusinessContext() {
        return businessContext;
    }
    
    public void setBusinessContext(String businessContext) {
        this.businessContext = businessContext;
    }
    
    public LockStatus getStatus() {
        return status;
    }
    
    public void setStatus(LockStatus status) {
        this.status = status;
    }
    
    public String getLockType() {
        return lockType;
    }
    
    public void setLockType(String lockType) {
        this.lockType = lockType;
    }
    
    /**
     * 檢查鎖是否已過期
     */
    public boolean isExpired() {
        if (remainingTime <= 0) {
            this.status = LockStatus.EXPIRED;
            return true;
        }
        return false;
    }
    
    /**
     * 獲取鎖持有時長（秒）
     */
    public long getHoldDuration() {
        return java.time.Duration.between(acquireTime, LocalDateTime.now()).getSeconds();
    }
    
    @Override
    public String toString() {
        return "LockInfo{" +
                "lockKey='" + lockKey + '\'' +
                ", holder='" + holder + '\'' +
                ", serviceSource='" + serviceSource + '\'' +
                ", acquireTime=" + acquireTime +
                ", leaseTime=" + leaseTime +
                ", remainingTime=" + remainingTime +
                ", businessContext='" + businessContext + '\'' +
                ", status=" + status +
                ", lockType='" + lockType + '\'' +
                '}';
    }
    
    /**
     * 鎖狀態枚舉
     */
    public enum LockStatus {
        ACTIVE,    // 活躍狀態
        EXPIRED,   // 已過期
        RELEASED   // 已釋放
    }
}