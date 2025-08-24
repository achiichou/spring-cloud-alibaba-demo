package com.atguigu.business.lock.dto;

import com.atguigu.business.lock.LockMonitorService;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 鎖衝突信息DTO - 用於API響應
 * 
 * @author Kiro
 */
public class LockConflictInfoDTO {
    
    private String lockKey;
    private String currentHolder;
    private String currentHolderService;
    private List<String> waitingServices;
    private LocalDateTime conflictStartTime;
    private int conflictCount;
    private long conflictDuration; // 衝突持續時間（毫秒）
    
    public LockConflictInfoDTO() {}
    
    /**
     * 從LockConflictInfo轉換為DTO
     */
    public static LockConflictInfoDTO fromLockConflictInfo(LockMonitorService.LockConflictInfo conflictInfo) {
        LockConflictInfoDTO dto = new LockConflictInfoDTO();
        dto.setLockKey(conflictInfo.getLockKey());
        dto.setCurrentHolder(conflictInfo.getCurrentHolder());
        dto.setCurrentHolderService(conflictInfo.getCurrentHolderService());
        dto.setWaitingServices(conflictInfo.getWaitingServices());
        dto.setConflictStartTime(java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(conflictInfo.getConflictStartTime()),
                java.time.ZoneId.systemDefault()));
        dto.setConflictCount(conflictInfo.getConflictCount());
        dto.setConflictDuration(System.currentTimeMillis() - conflictInfo.getConflictStartTime());
        return dto;
    }
    
    // Getters and Setters
    public String getLockKey() {
        return lockKey;
    }
    
    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }
    
    public String getCurrentHolder() {
        return currentHolder;
    }
    
    public void setCurrentHolder(String currentHolder) {
        this.currentHolder = currentHolder;
    }
    
    public String getCurrentHolderService() {
        return currentHolderService;
    }
    
    public void setCurrentHolderService(String currentHolderService) {
        this.currentHolderService = currentHolderService;
    }
    
    public List<String> getWaitingServices() {
        return waitingServices;
    }
    
    public void setWaitingServices(List<String> waitingServices) {
        this.waitingServices = waitingServices;
    }
    
    public LocalDateTime getConflictStartTime() {
        return conflictStartTime;
    }
    
    public void setConflictStartTime(LocalDateTime conflictStartTime) {
        this.conflictStartTime = conflictStartTime;
    }
    
    public int getConflictCount() {
        return conflictCount;
    }
    
    public void setConflictCount(int conflictCount) {
        this.conflictCount = conflictCount;
    }
    
    public long getConflictDuration() {
        return conflictDuration;
    }
    
    public void setConflictDuration(long conflictDuration) {
        this.conflictDuration = conflictDuration;
    }
}