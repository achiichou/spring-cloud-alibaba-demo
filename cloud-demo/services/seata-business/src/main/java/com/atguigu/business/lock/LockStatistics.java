package com.atguigu.business.lock;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 跨服務分布式鎖統計數據模型
 * 
 * @author Kiro
 */
public class LockStatistics {
    
    /**
     * 統計時間範圍開始
     */
    private LocalDateTime statisticsStartTime;
    
    /**
     * 統計時間範圍結束
     */
    private LocalDateTime statisticsEndTime;
    
    /**
     * 總鎖請求數
     */
    private long totalLockRequests;
    
    /**
     * 成功獲取鎖數
     */
    private long successfulLocks;
    
    /**
     * 失敗鎖請求數
     */
    private long failedLocks;
    
    /**
     * 超時的鎖請求數
     */
    private long timeoutLocks;
    
    /**
     * 平均等待時間（毫秒）
     */
    private long averageWaitTime;
    
    /**
     * 平均持有時間（毫秒）
     */
    private long averageHoldTime;
    
    /**
     * 最大等待時間（毫秒）
     */
    private long maxWaitTime;
    
    /**
     * 最大持有時間（毫秒）
     */
    private long maxHoldTime;
    
    /**
     * 各鎖鍵的統計信息
     * Key: lockKey, Value: 該鎖鍵的請求次數
     */
    private Map<String, Long> lockKeyStats;
    
    /**
     * 各服務來源的統計信息
     * Key: serviceSource, Value: ServiceLockStats
     */
    private Map<String, ServiceLockStats> serviceStats;
    
    /**
     * 跨服務鎖衝突次數
     */
    private long crossServiceConflicts;
    
    /**
     * 當前活躍鎖數量
     */
    private int currentActiveLocks;
    
    /**
     * 鎖成功率（百分比）
     */
    private double successRate;
    
    public LockStatistics() {
        this.lockKeyStats = new HashMap<>();
        this.serviceStats = new HashMap<>();
        this.statisticsStartTime = LocalDateTime.now();
    }
    
    // Getters and Setters
    public LocalDateTime getStatisticsStartTime() {
        return statisticsStartTime;
    }
    
    public void setStatisticsStartTime(LocalDateTime statisticsStartTime) {
        this.statisticsStartTime = statisticsStartTime;
    }
    
    public LocalDateTime getStatisticsEndTime() {
        return statisticsEndTime;
    }
    
    public void setStatisticsEndTime(LocalDateTime statisticsEndTime) {
        this.statisticsEndTime = statisticsEndTime;
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
    
    public long getFailedLocks() {
        return failedLocks;
    }
    
    public void setFailedLocks(long failedLocks) {
        this.failedLocks = failedLocks;
    }
    
    public long getTimeoutLocks() {
        return timeoutLocks;
    }
    
    public void setTimeoutLocks(long timeoutLocks) {
        this.timeoutLocks = timeoutLocks;
    }
    
    public long getAverageWaitTime() {
        return averageWaitTime;
    }
    
    public void setAverageWaitTime(long averageWaitTime) {
        this.averageWaitTime = averageWaitTime;
    }
    
    public long getAverageHoldTime() {
        return averageHoldTime;
    }
    
    public void setAverageHoldTime(long averageHoldTime) {
        this.averageHoldTime = averageHoldTime;
    }
    
    public long getMaxWaitTime() {
        return maxWaitTime;
    }
    
    public void setMaxWaitTime(long maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }
    
    public long getMaxHoldTime() {
        return maxHoldTime;
    }
    
    public void setMaxHoldTime(long maxHoldTime) {
        this.maxHoldTime = maxHoldTime;
    }
    
    public Map<String, Long> getLockKeyStats() {
        return lockKeyStats;
    }
    
    public void setLockKeyStats(Map<String, Long> lockKeyStats) {
        this.lockKeyStats = lockKeyStats;
    }
    
    public Map<String, ServiceLockStats> getServiceStats() {
        return serviceStats;
    }
    
    public void setServiceStats(Map<String, ServiceLockStats> serviceStats) {
        this.serviceStats = serviceStats;
    }
    
    public long getCrossServiceConflicts() {
        return crossServiceConflicts;
    }
    
    public void setCrossServiceConflicts(long crossServiceConflicts) {
        this.crossServiceConflicts = crossServiceConflicts;
    }
    
    public int getCurrentActiveLocks() {
        return currentActiveLocks;
    }
    
    public void setCurrentActiveLocks(int currentActiveLocks) {
        this.currentActiveLocks = currentActiveLocks;
    }
    
    public double getSuccessRate() {
        return successRate;
    }
    
    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }
    
    /**
     * 計算成功率
     */
    public void calculateSuccessRate() {
        if (totalLockRequests > 0) {
            this.successRate = (double) successfulLocks / totalLockRequests * 100;
        } else {
            this.successRate = 0.0;
        }
    }
    
    /**
     * 添加鎖鍵統計
     */
    public void addLockKeyStats(String lockKey) {
        lockKeyStats.merge(lockKey, 1L, Long::sum);
    }
    
    /**
     * 添加服務統計
     */
    public void addServiceStats(String serviceSource, boolean success, long waitTime, long holdTime) {
        ServiceLockStats stats = serviceStats.computeIfAbsent(serviceSource, k -> new ServiceLockStats(serviceSource));
        stats.addRequest(success, waitTime, holdTime);
    }
    
    /**
     * 增加跨服務衝突計數
     */
    public void incrementCrossServiceConflicts() {
        this.crossServiceConflicts++;
    }
    
    @Override
    public String toString() {
        return "LockStatistics{" +
                "statisticsStartTime=" + statisticsStartTime +
                ", statisticsEndTime=" + statisticsEndTime +
                ", totalLockRequests=" + totalLockRequests +
                ", successfulLocks=" + successfulLocks +
                ", failedLocks=" + failedLocks +
                ", timeoutLocks=" + timeoutLocks +
                ", averageWaitTime=" + averageWaitTime +
                ", averageHoldTime=" + averageHoldTime +
                ", maxWaitTime=" + maxWaitTime +
                ", maxHoldTime=" + maxHoldTime +
                ", crossServiceConflicts=" + crossServiceConflicts +
                ", currentActiveLocks=" + currentActiveLocks +
                ", successRate=" + successRate +
                '}';
    }
    
    /**
     * 服務鎖統計內部類
     */
    public static class ServiceLockStats {
        private String serviceName;
        private long totalRequests;
        private long successfulRequests;
        private long failedRequests;
        private long totalWaitTime;
        private long totalHoldTime;
        private long maxWaitTime;
        private long maxHoldTime;
        
        public ServiceLockStats(String serviceName) {
            this.serviceName = serviceName;
        }
        
        public void addRequest(boolean success, long waitTime, long holdTime) {
            totalRequests++;
            if (success) {
                successfulRequests++;
            } else {
                failedRequests++;
            }
            
            totalWaitTime += waitTime;
            totalHoldTime += holdTime;
            
            if (waitTime > maxWaitTime) {
                maxWaitTime = waitTime;
            }
            if (holdTime > maxHoldTime) {
                maxHoldTime = holdTime;
            }
        }
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests * 100 : 0.0;
        }
        
        public long getAverageWaitTime() {
            return totalRequests > 0 ? totalWaitTime / totalRequests : 0;
        }
        
        public long getAverageHoldTime() {
            return successfulRequests > 0 ? totalHoldTime / successfulRequests : 0;
        }
        
        // Getters and Setters
        public String getServiceName() {
            return serviceName;
        }
        
        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
        
        public long getTotalRequests() {
            return totalRequests;
        }
        
        public void setTotalRequests(long totalRequests) {
            this.totalRequests = totalRequests;
        }
        
        public long getSuccessfulRequests() {
            return successfulRequests;
        }
        
        public void setSuccessfulRequests(long successfulRequests) {
            this.successfulRequests = successfulRequests;
        }
        
        public long getFailedRequests() {
            return failedRequests;
        }
        
        public void setFailedRequests(long failedRequests) {
            this.failedRequests = failedRequests;
        }
        
        public long getTotalWaitTime() {
            return totalWaitTime;
        }
        
        public void setTotalWaitTime(long totalWaitTime) {
            this.totalWaitTime = totalWaitTime;
        }
        
        public long getTotalHoldTime() {
            return totalHoldTime;
        }
        
        public void setTotalHoldTime(long totalHoldTime) {
            this.totalHoldTime = totalHoldTime;
        }
        
        public long getMaxWaitTime() {
            return maxWaitTime;
        }
        
        public void setMaxWaitTime(long maxWaitTime) {
            this.maxWaitTime = maxWaitTime;
        }
        
        public long getMaxHoldTime() {
            return maxHoldTime;
        }
        
        public void setMaxHoldTime(long maxHoldTime) {
            this.maxHoldTime = maxHoldTime;
        }
    }
}