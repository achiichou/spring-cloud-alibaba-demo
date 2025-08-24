package com.atguigu.business.lock.dto;

import com.atguigu.business.lock.LockStatistics;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 鎖統計信息DTO - 用於API響應
 * 
 * @author Kiro
 */
public class LockStatisticsDTO {
    
    private LocalDateTime statisticsStartTime;
    private LocalDateTime statisticsEndTime;
    private long totalLockRequests;
    private long successfulLocks;
    private long failedLocks;
    private long timeoutLocks;
    private long averageWaitTime;
    private long averageHoldTime;
    private long maxWaitTime;
    private long maxHoldTime;
    private Map<String, Long> lockKeyStats;
    private Map<String, ServiceLockStatsDTO> serviceStats;
    private long crossServiceConflicts;
    private int currentActiveLocks;
    private double successRate;
    
    public LockStatisticsDTO() {
        this.lockKeyStats = new HashMap<>();
        this.serviceStats = new HashMap<>();
    }
    
    /**
     * 從LockStatistics轉換為DTO
     */
    public static LockStatisticsDTO fromLockStatistics(LockStatistics statistics) {
        LockStatisticsDTO dto = new LockStatisticsDTO();
        dto.setStatisticsStartTime(statistics.getStatisticsStartTime());
        dto.setStatisticsEndTime(statistics.getStatisticsEndTime());
        dto.setTotalLockRequests(statistics.getTotalLockRequests());
        dto.setSuccessfulLocks(statistics.getSuccessfulLocks());
        dto.setFailedLocks(statistics.getFailedLocks());
        dto.setTimeoutLocks(statistics.getTimeoutLocks());
        dto.setAverageWaitTime(statistics.getAverageWaitTime());
        dto.setAverageHoldTime(statistics.getAverageHoldTime());
        dto.setMaxWaitTime(statistics.getMaxWaitTime());
        dto.setMaxHoldTime(statistics.getMaxHoldTime());
        dto.setLockKeyStats(new HashMap<>(statistics.getLockKeyStats()));
        dto.setCrossServiceConflicts(statistics.getCrossServiceConflicts());
        dto.setCurrentActiveLocks(statistics.getCurrentActiveLocks());
        dto.setSuccessRate(statistics.getSuccessRate());
        
        // 轉換服務統計信息
        Map<String, ServiceLockStatsDTO> serviceStatsDTO = statistics.getServiceStats()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> ServiceLockStatsDTO.fromServiceLockStats(entry.getValue())
                ));
        dto.setServiceStats(serviceStatsDTO);
        
        return dto;
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
    
    public Map<String, ServiceLockStatsDTO> getServiceStats() {
        return serviceStats;
    }
    
    public void setServiceStats(Map<String, ServiceLockStatsDTO> serviceStats) {
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
     * 服務鎖統計DTO內部類
     */
    public static class ServiceLockStatsDTO {
        private String serviceName;
        private long totalRequests;
        private long successfulRequests;
        private long failedRequests;
        private long maxWaitTime;
        private long maxHoldTime;
        private double successRate;
        private long averageWaitTime;
        private long averageHoldTime;
        
        public ServiceLockStatsDTO() {}
        
        /**
         * 從ServiceLockStats轉換為DTO
         */
        public static ServiceLockStatsDTO fromServiceLockStats(LockStatistics.ServiceLockStats stats) {
            ServiceLockStatsDTO dto = new ServiceLockStatsDTO();
            dto.setServiceName(stats.getServiceName());
            dto.setTotalRequests(stats.getTotalRequests());
            dto.setSuccessfulRequests(stats.getSuccessfulRequests());
            dto.setFailedRequests(stats.getFailedRequests());
            dto.setMaxWaitTime(stats.getMaxWaitTime());
            dto.setMaxHoldTime(stats.getMaxHoldTime());
            dto.setSuccessRate(stats.getSuccessRate());
            dto.setAverageWaitTime(stats.getAverageWaitTime());
            dto.setAverageHoldTime(stats.getAverageHoldTime());
            return dto;
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
        
        public double getSuccessRate() {
            return successRate;
        }
        
        public void setSuccessRate(double successRate) {
            this.successRate = successRate;
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
    }
}