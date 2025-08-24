package com.atguigu.business.lock.dto;

import com.atguigu.business.lock.LockInfo;
import java.time.LocalDateTime;

/**
 * 鎖信息DTO - 用於API響應
 * 
 * @author Kiro
 */
public class LockInfoDTO {
    
    private String lockKey;
    private String holder;
    private String serviceSource;
    private LocalDateTime acquireTime;
    private long leaseTime;
    private long remainingTime;
    private String businessContext;
    private String status;
    private String lockType;
    private long holdDuration;
    private boolean expired;
    
    public LockInfoDTO() {}
    
    /**
     * 從LockInfo轉換為DTO
     */
    public static LockInfoDTO fromLockInfo(LockInfo lockInfo) {
        LockInfoDTO dto = new LockInfoDTO();
        dto.setLockKey(lockInfo.getLockKey());
        dto.setHolder(lockInfo.getHolder());
        dto.setServiceSource(lockInfo.getServiceSource());
        dto.setAcquireTime(lockInfo.getAcquireTime());
        dto.setLeaseTime(lockInfo.getLeaseTime());
        dto.setRemainingTime(lockInfo.getRemainingTime());
        dto.setBusinessContext(lockInfo.getBusinessContext());
        dto.setStatus(lockInfo.getStatus() != null ? lockInfo.getStatus().name() : null);
        dto.setLockType(lockInfo.getLockType());
        dto.setHoldDuration(lockInfo.getHoldDuration());
        dto.setExpired(lockInfo.isExpired());
        return dto;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getLockType() {
        return lockType;
    }
    
    public void setLockType(String lockType) {
        this.lockType = lockType;
    }
    
    public long getHoldDuration() {
        return holdDuration;
    }
    
    public void setHoldDuration(long holdDuration) {
        this.holdDuration = holdDuration;
    }
    
    public boolean isExpired() {
        return expired;
    }
    
    public void setExpired(boolean expired) {
        this.expired = expired;
    }
}