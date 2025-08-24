package com.atguigu.business.lock.dto;

import com.atguigu.business.lock.LockMonitorService;

/**
 * 服務鎖使用情況DTO - 用於API響應
 * 
 * @author Kiro
 */
public class ServiceLockUsageDTO {
    
    private String serviceName;
    private int activeLocks;
    private long totalLockRequests;
    private long successfulLocks;
    private double successRate;
    private long averageHoldTime;
    private long maxHoldTime;
    private long totalConflicts;
    
    public ServiceLockUsageDTO() {}
    
    /**
     * 從ServiceLockUsage轉換為DTO
     */
    public static ServiceLockUsageDTO fromServiceLockUsage(LockMonitorService.ServiceLockUsage usage) {
        ServiceLockUsageDTO dto = new ServiceLockUsageDTO();
        dto.setServiceName(usage.getServiceName());
        dto.setActiveLocks(usage.getActiveLocks());
        dto.setTotalLockRequests(usage.getTotalLockRequests());
        dto.setSuccessfulLocks(usage.getSuccessfulLocks());
        dto.setSuccessRate(usage.getSuccessRate());
        dto.setAverageHoldTime(usage.getAverageHoldTime());
        return dto;
    }
    
    // Getters and Setters
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public int getActiveLocks() {
        return activeLocks;
    }
    
    public void setActiveLocks(int activeLocks) {
        this.activeLocks = activeLocks;
    }
    
    public long getTotalLockRequests() {
        return totalLockRequests;
    }
    
    public void setTotalLockRequests(long totalLockRequests) {
        this.totalLockRequests = totalLockRequests;
    }
    
    public long getSuccessfulLocks() {
        return successfulLocks;
    }
    
    public void setSuccessfulLocks(long successfulLocks) {
        this.successfulLocks = successfulLocks;
    }
    
    public double getSuccessRate() {
        return successRate;
    }
    
    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }
    
    public long getAverageHoldTime() {
        return averageHoldTime;
    }
    
    public void setAverageHoldTime(long averageHoldTime) {
        this.averageHoldTime = averageHoldTime;
    }
    
    public long getMaxHoldTime() {
        return maxHoldTime;
    }
    
    public void setMaxHoldTime(long maxHoldTime) {
        this.maxHoldTime = maxHoldTime;
    }
    
    public long getTotalConflicts() {
        return totalConflicts;
    }
    
    public void setTotalConflicts(long totalConflicts) {
        this.totalConflicts = totalConflicts;
    }
}