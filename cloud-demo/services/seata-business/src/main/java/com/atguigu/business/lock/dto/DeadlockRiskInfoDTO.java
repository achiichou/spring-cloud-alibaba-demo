package com.atguigu.business.lock.dto;

import com.atguigu.business.lock.LockMonitorService;
import java.util.List;

/**
 * 死鎖風險信息DTO - 用於API響應
 * 
 * @author Kiro
 */
public class DeadlockRiskInfoDTO {
    
    private String lockKey;
    private String holderService;
    private List<String> waitingServices;
    private long riskScore;
    private String riskDescription;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    public DeadlockRiskInfoDTO() {}
    
    /**
     * 從DeadlockRiskInfo轉換為DTO
     */
    public static DeadlockRiskInfoDTO fromDeadlockRiskInfo(LockMonitorService.DeadlockRiskInfo riskInfo) {
        DeadlockRiskInfoDTO dto = new DeadlockRiskInfoDTO();
        dto.setLockKey(riskInfo.getLockKey());
        dto.setHolderService(riskInfo.getHolderService());
        dto.setWaitingServices(riskInfo.getWaitingServices());
        dto.setRiskScore(riskInfo.getRiskScore());
        dto.setRiskDescription(riskInfo.getRiskDescription());
        dto.setRiskLevel(calculateRiskLevel(riskInfo.getRiskScore()));
        return dto;
    }
    
    /**
     * 根據風險分數計算風險等級
     */
    private static String calculateRiskLevel(long riskScore) {
        if (riskScore >= 80) {
            return "CRITICAL";
        } else if (riskScore >= 60) {
            return "HIGH";
        } else if (riskScore >= 40) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    // Getters and Setters
    public String getLockKey() {
        return lockKey;
    }
    
    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }
    
    public String getHolderService() {
        return holderService;
    }
    
    public void setHolderService(String holderService) {
        this.holderService = holderService;
    }
    
    public List<String> getWaitingServices() {
        return waitingServices;
    }
    
    public void setWaitingServices(List<String> waitingServices) {
        this.waitingServices = waitingServices;
    }
    
    public long getRiskScore() {
        return riskScore;
    }
    
    public void setRiskScore(long riskScore) {
        this.riskScore = riskScore;
    }
    
    public String getRiskDescription() {
        return riskDescription;
    }
    
    public void setRiskDescription(String riskDescription) {
        this.riskDescription = riskDescription;
    }
    
    public String getRiskLevel() {
        return riskLevel;
    }
    
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
}