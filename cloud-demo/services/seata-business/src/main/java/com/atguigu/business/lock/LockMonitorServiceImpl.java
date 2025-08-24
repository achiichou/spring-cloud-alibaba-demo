package com.atguigu.business.lock;

import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 跨服務分布式鎖監控服務實現
 * 
 * @author Kiro
 */
@Service
public class LockMonitorServiceImpl implements LockMonitorService {
    
    private static final Logger logger = LoggerFactory.getLogger(LockMonitorServiceImpl.class);
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private RedisDistributedLock redisDistributedLock;
    
    @Value("${distributed.lock.key-prefix:distributed:lock:storage:}")
    private String lockKeyPrefix;
    
    @Value("${spring.application.name:seata-business}")
    private String currentServiceName;
    
    // 統計數據存儲
    private final LockStatistics globalStatistics = new LockStatistics();
    private final Map<String, LockConflictInfo> conflictInfoMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lockEventCounters = new ConcurrentHashMap<>();
    
    // 鎖事件記錄
    private final Map<String, List<LockEventRecord>> lockEventHistory = new ConcurrentHashMap<>();
    
    @Override
    public List<LockInfo> getAllLocks() {
        List<LockInfo> allLocks = new ArrayList<>();
        
        try {
            RKeys keys = redissonClient.getKeys();
            Iterable<String> lockKeys = keys.getKeysByPattern(lockKeyPrefix + "*");
            
            for (String lockKey : lockKeys) {
                LockInfo lockInfo = getLockInfo(lockKey);
                if (lockInfo != null) {
                    allLocks.add(lockInfo);
                }
            }
            
            logger.debug("Retrieved {} active locks", allLocks.size());
        } catch (Exception e) {
            logger.error("Error retrieving all locks", e);
        }
        
        return allLocks;
    }
    
    @Override
    public LockInfo getLockInfo(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            
            if (!lock.isLocked()) {
                return null;
            }
            
            LockInfo lockInfo = new LockInfo();
            lockInfo.setLockKey(lockKey);
            lockInfo.setRemainingTime(lock.remainTimeToLive() / 1000); // 轉換為秒
            lockInfo.setStatus(LockInfo.LockStatus.ACTIVE);
            
            // 嘗試從鎖的元數據中獲取服務來源信息
            String serviceSource = extractServiceFromLockKey(lockKey);
            lockInfo.setServiceSource(serviceSource);
            
            // 設置鎖類型
            if (lockKey.contains("batch:")) {
                lockInfo.setLockType("BATCH_OPERATION");
            } else {
                lockInfo.setLockType("STORAGE_DEDUCT");
            }
            
            // 估算獲取時間（基於剩餘時間）
            long estimatedAcquireTime = System.currentTimeMillis() - 
                (30000 - lock.remainTimeToLive()); // 假設默認租約時間30秒
            lockInfo.setAcquireTime(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(estimatedAcquireTime), 
                ZoneId.systemDefault()));
            
            // 設置業務上下文
            lockInfo.setBusinessContext(generateBusinessContext(lockKey, serviceSource));
            
            return lockInfo;
        } catch (Exception e) {
            logger.error("Error getting lock info for key: {}", lockKey, e);
            return null;
        }
    }
    
    @Override
    public List<LockInfo> getLocksByService(String serviceSource) {
        return getAllLocks().stream()
                .filter(lockInfo -> serviceSource.equals(lockInfo.getServiceSource()))
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean forceUnlock(String lockKey) {
        try {
            boolean result = redisDistributedLock.forceUnlock(lockKey);
            
            if (result) {
                recordLockEvent(lockKey, currentServiceName, LockOperation.FORCE_UNLOCK, true, 0);
                logger.warn("Force unlocked key: {} by service: {}", lockKey, currentServiceName);
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error force unlocking key: {}", lockKey, e);
            recordLockEvent(lockKey, currentServiceName, LockOperation.FORCE_UNLOCK, false, 0);
            return false;
        }
    }
    
    @Override
    public List<String> batchForceUnlock(List<String> lockKeys) {
        List<String> successfullyUnlocked = new ArrayList<>();
        
        for (String lockKey : lockKeys) {
            if (forceUnlock(lockKey)) {
                successfullyUnlocked.add(lockKey);
            }
        }
        
        logger.info("Batch force unlock completed. Successfully unlocked {}/{} locks", 
                   successfullyUnlocked.size(), lockKeys.size());
        
        return successfullyUnlocked;
    }
    
    @Override
    public LockStatistics getLockStatistics() {
        // 更新當前活躍鎖數量
        globalStatistics.setCurrentActiveLocks(getActiveLockCount());
        globalStatistics.setStatisticsEndTime(LocalDateTime.now());
        globalStatistics.calculateSuccessRate();
        
        return globalStatistics;
    }
    
    @Override
    public void resetStatistics() {
        globalStatistics.setTotalLockRequests(0);
        globalStatistics.setSuccessfulLocks(0);
        globalStatistics.setFailedLocks(0);
        globalStatistics.setTimeoutLocks(0);
        globalStatistics.setCrossServiceConflicts(0);
        globalStatistics.setAverageWaitTime(0);
        globalStatistics.setAverageHoldTime(0);
        globalStatistics.setMaxWaitTime(0);
        globalStatistics.setMaxHoldTime(0);
        globalStatistics.setSuccessRate(0.0);
        globalStatistics.getLockKeyStats().clear();
        globalStatistics.getServiceStats().clear();
        globalStatistics.setStatisticsStartTime(LocalDateTime.now());
        
        // 清理衝突信息和事件記錄
        conflictInfoMap.clear();
        lockEventCounters.clear();
        lockEventHistory.clear();
        
        logger.info("Lock statistics reset by service: {}", currentServiceName);
    }
    
    @Override
    public Map<String, LockConflictInfo> detectCrossServiceConflicts() {
        Map<String, LockConflictInfo> conflicts = new HashMap<>();
        
        try {
            List<LockInfo> allLocks = getAllLocks();
            Map<String, List<LockInfo>> locksByKey = allLocks.stream()
                    .collect(Collectors.groupingBy(LockInfo::getLockKey));
            
            for (Map.Entry<String, List<LockInfo>> entry : locksByKey.entrySet()) {
                String lockKey = entry.getKey();
                List<LockInfo> locksForKey = entry.getValue();
                
                if (locksForKey.size() > 1) {
                    // 檢測到潛在衝突
                    LockInfo activeLock = locksForKey.stream()
                            .filter(lock -> lock.getStatus() == LockInfo.LockStatus.ACTIVE)
                            .findFirst()
                            .orElse(null);
                    
                    if (activeLock != null) {
                        LockConflictInfo conflictInfo = new LockConflictInfo(
                                lockKey, 
                                activeLock.getHolder(), 
                                activeLock.getServiceSource()
                        );
                        
                        List<String> waitingServices = locksForKey.stream()
                                .filter(lock -> !lock.equals(activeLock))
                                .map(LockInfo::getServiceSource)
                                .distinct()
                                .collect(Collectors.toList());
                        
                        conflictInfo.setWaitingServices(waitingServices);
                        conflictInfo.setConflictCount(waitingServices.size());
                        
                        conflicts.put(lockKey, conflictInfo);
                        
                        // 更新全局衝突統計
                        globalStatistics.incrementCrossServiceConflicts();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error detecting cross-service conflicts", e);
        }
        
        return conflicts;
    }
    
    @Override
    public LockStatistics getLockStatistics(long startTimeMillis, long endTimeMillis) {
        // 創建時間範圍內的統計數據
        LockStatistics rangeStats = new LockStatistics();
        rangeStats.setStatisticsStartTime(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(startTimeMillis), ZoneId.systemDefault()));
        rangeStats.setStatisticsEndTime(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(endTimeMillis), ZoneId.systemDefault()));
        
        // 過濾時間範圍內的事件記錄
        for (Map.Entry<String, List<LockEventRecord>> entry : lockEventHistory.entrySet()) {
            List<LockEventRecord> eventsInRange = entry.getValue().stream()
                    .filter(event -> event.getTimestamp() >= startTimeMillis && 
                                   event.getTimestamp() <= endTimeMillis)
                    .collect(Collectors.toList());
            
            // 計算統計數據
            long totalRequests = eventsInRange.stream()
                    .filter(event -> event.getOperation() == LockOperation.ACQUIRE)
                    .count();
            long successfulRequests = eventsInRange.stream()
                    .filter(event -> event.getOperation() == LockOperation.ACQUIRE && event.isSuccess())
                    .count();
            
            rangeStats.setTotalLockRequests(rangeStats.getTotalLockRequests() + totalRequests);
            rangeStats.setSuccessfulLocks(rangeStats.getSuccessfulLocks() + successfulRequests);
            rangeStats.setFailedLocks(rangeStats.getFailedLocks() + (totalRequests - successfulRequests));
        }
        
        rangeStats.calculateSuccessRate();
        return rangeStats;
    }
    
    @Override
    public int getActiveLockCount() {
        try {
            RKeys keys = redissonClient.getKeys();
            Iterable<String> lockKeys = keys.getKeysByPattern(lockKeyPrefix + "*");
            int count = 0;
            for (String key : lockKeys) {
                if (redissonClient.getLock(key).isLocked()) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            logger.error("Error getting active lock count", e);
            return 0;
        }
    }
    
    @Override
    public Map<String, ServiceLockUsage> getServiceLockUsage() {
        Map<String, ServiceLockUsage> serviceUsage = new HashMap<>();
        
        // 從全局統計中獲取各服務的使用情況
        for (Map.Entry<String, LockStatistics.ServiceLockStats> entry : 
             globalStatistics.getServiceStats().entrySet()) {
            
            String serviceName = entry.getKey();
            LockStatistics.ServiceLockStats stats = entry.getValue();
            
            ServiceLockUsage usage = new ServiceLockUsage(serviceName);
            usage.setTotalLockRequests(stats.getTotalRequests());
            usage.setSuccessfulLocks(stats.getSuccessfulRequests());
            usage.setSuccessRate(stats.getSuccessRate());
            usage.setAverageHoldTime(stats.getAverageHoldTime());
            
            // 計算當前活躍鎖數量
            int activeLocks = (int) getAllLocks().stream()
                    .filter(lock -> serviceName.equals(lock.getServiceSource()))
                    .count();
            usage.setActiveLocks(activeLocks);
            
            serviceUsage.put(serviceName, usage);
        }
        
        return serviceUsage;
    }
    
    @Override
    public List<DeadlockRiskInfo> detectDeadlockRisk() {
        List<DeadlockRiskInfo> riskInfos = new ArrayList<>();
        
        try {
            Map<String, LockConflictInfo> conflicts = detectCrossServiceConflicts();
            
            for (LockConflictInfo conflict : conflicts.values()) {
                if (conflict.getWaitingServices().size() > 1) {
                    DeadlockRiskInfo riskInfo = new DeadlockRiskInfo(
                            conflict.getLockKey(), 
                            conflict.getCurrentHolderService()
                    );
                    riskInfo.setWaitingServices(conflict.getWaitingServices());
                    
                    // 計算風險分數（基於等待服務數量和衝突持續時間）
                    long conflictDuration = System.currentTimeMillis() - conflict.getConflictStartTime();
                    long riskScore = conflict.getWaitingServices().size() * 10 + (conflictDuration / 1000);
                    riskInfo.setRiskScore(riskScore);
                    
                    riskInfo.setRiskDescription(String.format(
                            "Lock %s held by %s with %d waiting services for %d seconds",
                            conflict.getLockKey(),
                            conflict.getCurrentHolderService(),
                            conflict.getWaitingServices().size(),
                            conflictDuration / 1000
                    ));
                    
                    riskInfos.add(riskInfo);
                }
            }
        } catch (Exception e) {
            logger.error("Error detecting deadlock risk", e);
        }
        
        return riskInfos;
    }
    
    @Override
    public List<LockInfo> getLongHeldLocks(long thresholdSeconds) {
        return getAllLocks().stream()
                .filter(lockInfo -> lockInfo.getHoldDuration() > thresholdSeconds)
                .collect(Collectors.toList());
    }
    
    @Override
    public void recordLockEvent(String lockKey, String serviceSource, LockOperation operation, 
                               boolean success, long duration) {
        try {
            // 記錄事件
            LockEventRecord event = new LockEventRecord(lockKey, serviceSource, operation, success, duration);
            lockEventHistory.computeIfAbsent(lockKey, k -> new ArrayList<>()).add(event);
            
            // 更新統計計數器
            String counterKey = serviceSource + ":" + operation.name() + ":" + success;
            lockEventCounters.computeIfAbsent(counterKey, k -> new AtomicLong(0)).incrementAndGet();
            
            // 更新全局統計
            updateGlobalStatistics(lockKey, serviceSource, operation, success, duration);
            
            logger.debug("Recorded lock event: {} for key: {} by service: {}", 
                        operation, lockKey, serviceSource);
        } catch (Exception e) {
            logger.error("Error recording lock event", e);
        }
    }
    
    @Override
    public void recordTransactionLockEvent(String lockKey, String xid, String serviceSource, 
                                          TransactionLockOperation operation) {
        try {
            // 記錄事務鎖事件
            TransactionLockEventRecord event = new TransactionLockEventRecord(
                lockKey, xid, serviceSource, operation);
            
            String eventKey = "tx:" + lockKey;
            lockEventHistory.computeIfAbsent(eventKey, k -> new ArrayList<>()).add(event);
            
            // 更新事務鎖統計計數器
            String counterKey = serviceSource + ":tx:" + operation.name();
            lockEventCounters.computeIfAbsent(counterKey, k -> new AtomicLong(0)).incrementAndGet();
            
            logger.debug("Recorded transaction lock event: {} for key: {} with XID: {} by service: {}", 
                        operation, lockKey, xid, serviceSource);
        } catch (Exception e) {
            logger.error("Error recording transaction lock event", e);
        }
    }
    
    /**
     * 從鎖鍵中提取服務來源信息
     */
    private String extractServiceFromLockKey(String lockKey) {
        // 這裡可以根據鎖鍵的命名規則來推斷服務來源
        // 或者從Redis中存儲的元數據中獲取
        
        // 簡單的推斷邏輯：檢查當前服務是否持有該鎖
        if (redisDistributedLock.isHeldByCurrentThread(lockKey)) {
            return currentServiceName;
        }
        
        // 如果不是當前服務持有，則可能是其他服務
        // 這裡可以實現更複雜的邏輯來確定服務來源
        return "unknown";
    }
    
    /**
     * 生成業務上下文信息
     */
    private String generateBusinessContext(String lockKey, String serviceSource) {
        if (lockKey.contains("batch:")) {
            return serviceSource + "-batch-operation";
        } else {
            return serviceSource + "-storage-deduct";
        }
    }
    
    /**
     * 更新全局統計數據
     */
    private void updateGlobalStatistics(String lockKey, String serviceSource, LockOperation operation, 
                                      boolean success, long duration) {
        synchronized (globalStatistics) {
            if (operation == LockOperation.ACQUIRE) {
                globalStatistics.setTotalLockRequests(globalStatistics.getTotalLockRequests() + 1);
                if (success) {
                    globalStatistics.setSuccessfulLocks(globalStatistics.getSuccessfulLocks() + 1);
                } else {
                    globalStatistics.setFailedLocks(globalStatistics.getFailedLocks() + 1);
                }
                
                // 更新等待時間統計
                if (duration > globalStatistics.getMaxWaitTime()) {
                    globalStatistics.setMaxWaitTime(duration);
                }
            }
            
            if (operation == LockOperation.TIMEOUT) {
                globalStatistics.setTimeoutLocks(globalStatistics.getTimeoutLocks() + 1);
            }
            
            if (operation == LockOperation.CONFLICT) {
                globalStatistics.incrementCrossServiceConflicts();
            }
            
            // 更新鎖鍵統計
            globalStatistics.addLockKeyStats(lockKey);
            
            // 更新服務統計
            globalStatistics.addServiceStats(serviceSource, success, duration, 0);
        }
    }
    
    /**
     * 鎖事件記錄內部類
     */
    private static class LockEventRecord {
        private final String lockKey;
        private final String serviceSource;
        private final LockOperation operation;
        private final boolean success;
        private final long duration;
        private final long timestamp;
        
        public LockEventRecord(String lockKey, String serviceSource, LockOperation operation, 
                             boolean success, long duration) {
            this.lockKey = lockKey;
            this.serviceSource = serviceSource;
            this.operation = operation;
            this.success = success;
            this.duration = duration;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters
        public String getLockKey() { return lockKey; }
        public String getServiceSource() { return serviceSource; }
        public LockOperation getOperation() { return operation; }
        public boolean isSuccess() { return success; }
        public long getDuration() { return duration; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * 事務鎖事件記錄內部類
     */
    private static class TransactionLockEventRecord extends LockEventRecord {
        private final String xid;
        private final TransactionLockOperation transactionOperation;
        
        public TransactionLockEventRecord(String lockKey, String xid, String serviceSource, 
                                        TransactionLockOperation operation) {
            super(lockKey, serviceSource, LockOperation.ACQUIRE, true, 0);
            this.xid = xid;
            this.transactionOperation = operation;
        }
        
        // Getters
        public String getXid() { return xid; }
        public TransactionLockOperation getTransactionOperation() { return transactionOperation; }
    }
}