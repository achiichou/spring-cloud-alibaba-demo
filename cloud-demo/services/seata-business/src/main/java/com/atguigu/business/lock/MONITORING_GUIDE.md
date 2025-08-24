# 跨服務分布式鎖監控服務使用指南

## 概述

跨服務分布式鎖監控服務 (`LockMonitorService`) 提供了全面的鎖監控、統計和管理功能，支持跨服務的鎖狀態監控和衝突檢測。

## 主要功能

### 1. 鎖信息查詢

#### 獲取所有鎖信息
```java
@Autowired
private LockMonitorService lockMonitorService;

// 獲取當前所有活躍的鎖
List<LockInfo> allLocks = lockMonitorService.getAllLocks();
for (LockInfo lock : allLocks) {
    System.out.println("Lock Key: " + lock.getLockKey());
    System.out.println("Service Source: " + lock.getServiceSource());
    System.out.println("Remaining Time: " + lock.getRemainingTime() + " seconds");
    System.out.println("Status: " + lock.getStatus());
}
```

#### 獲取特定鎖信息
```java
String lockKey = "distributed:lock:storage:PRODUCT001";
LockInfo lockInfo = lockMonitorService.getLockInfo(lockKey);
if (lockInfo != null) {
    System.out.println("Lock is active, held by: " + lockInfo.getServiceSource());
} else {
    System.out.println("Lock does not exist or is not active");
}
```

#### 按服務來源查詢鎖
```java
// 獲取 seata-business 服務持有的所有鎖
List<LockInfo> businessLocks = lockMonitorService.getLocksByService("seata-business");

// 獲取 seata-storage 服務持有的所有鎖
List<LockInfo> storageLocks = lockMonitorService.getLocksByService("seata-storage");
```

### 2. 鎖管理功能

#### 強制釋放單個鎖
```java
String lockKey = "distributed:lock:storage:PRODUCT001";
boolean success = lockMonitorService.forceUnlock(lockKey);
if (success) {
    System.out.println("Lock successfully force unlocked");
} else {
    System.out.println("Failed to force unlock the lock");
}
```

#### 批量強制釋放鎖
```java
List<String> lockKeys = Arrays.asList(
    "distributed:lock:storage:PRODUCT001",
    "distributed:lock:storage:PRODUCT002",
    "distributed:lock:storage:PRODUCT003"
);

List<String> successfullyUnlocked = lockMonitorService.batchForceUnlock(lockKeys);
System.out.println("Successfully unlocked " + successfullyUnlocked.size() + " locks");
```

### 3. 統計信息收集

#### 獲取全局統計信息
```java
LockStatistics stats = lockMonitorService.getLockStatistics();
System.out.println("Total Lock Requests: " + stats.getTotalLockRequests());
System.out.println("Successful Locks: " + stats.getSuccessfulLocks());
System.out.println("Failed Locks: " + stats.getFailedLocks());
System.out.println("Success Rate: " + stats.getSuccessRate() + "%");
System.out.println("Cross-Service Conflicts: " + stats.getCrossServiceConflicts());
System.out.println("Current Active Locks: " + stats.getCurrentActiveLocks());
```

#### 獲取時間範圍內的統計
```java
long startTime = System.currentTimeMillis() - 3600000; // 1小時前
long endTime = System.currentTimeMillis();

LockStatistics rangeStats = lockMonitorService.getLockStatistics(startTime, endTime);
System.out.println("Locks in the last hour: " + rangeStats.getTotalLockRequests());
```

#### 重置統計信息
```java
lockMonitorService.resetStatistics();
System.out.println("Statistics have been reset");
```

### 4. 跨服務監控

#### 檢測跨服務鎖衝突
```java
Map<String, LockMonitorService.LockConflictInfo> conflicts = 
    lockMonitorService.detectCrossServiceConflicts();

for (Map.Entry<String, LockMonitorService.LockConflictInfo> entry : conflicts.entrySet()) {
    String lockKey = entry.getKey();
    LockMonitorService.LockConflictInfo conflict = entry.getValue();
    
    System.out.println("Conflict detected for lock: " + lockKey);
    System.out.println("Current holder service: " + conflict.getCurrentHolderService());
    System.out.println("Waiting services: " + conflict.getWaitingServices());
    System.out.println("Conflict count: " + conflict.getConflictCount());
}
```

#### 獲取各服務的鎖使用情況
```java
Map<String, LockMonitorService.ServiceLockUsage> serviceUsage = 
    lockMonitorService.getServiceLockUsage();

for (Map.Entry<String, LockMonitorService.ServiceLockUsage> entry : serviceUsage.entrySet()) {
    String serviceName = entry.getKey();
    LockMonitorService.ServiceLockUsage usage = entry.getValue();
    
    System.out.println("Service: " + serviceName);
    System.out.println("  Active Locks: " + usage.getActiveLocks());
    System.out.println("  Total Requests: " + usage.getTotalLockRequests());
    System.out.println("  Success Rate: " + usage.getSuccessRate() + "%");
    System.out.println("  Average Hold Time: " + usage.getAverageHoldTime() + "ms");
}
```

### 5. 風險檢測

#### 檢測死鎖風險
```java
List<LockMonitorService.DeadlockRiskInfo> risks = lockMonitorService.detectDeadlockRisk();

for (LockMonitorService.DeadlockRiskInfo risk : risks) {
    System.out.println("Deadlock risk detected:");
    System.out.println("  Lock Key: " + risk.getLockKey());
    System.out.println("  Holder Service: " + risk.getHolderService());
    System.out.println("  Waiting Services: " + risk.getWaitingServices());
    System.out.println("  Risk Score: " + risk.getRiskScore());
    System.out.println("  Description: " + risk.getRiskDescription());
}
```

#### 檢測長時間持有的鎖
```java
long thresholdSeconds = 60; // 60秒閾值
List<LockInfo> longHeldLocks = lockMonitorService.getLongHeldLocks(thresholdSeconds);

for (LockInfo lock : longHeldLocks) {
    System.out.println("Long-held lock detected:");
    System.out.println("  Lock Key: " + lock.getLockKey());
    System.out.println("  Service Source: " + lock.getServiceSource());
    System.out.println("  Hold Duration: " + lock.getHoldDuration() + " seconds");
    System.out.println("  Business Context: " + lock.getBusinessContext());
}
```

### 6. 事件記錄

#### 手動記錄鎖事件
```java
String lockKey = "distributed:lock:storage:PRODUCT001";
String serviceSource = "seata-business";
LockMonitorService.LockOperation operation = LockMonitorService.LockOperation.ACQUIRE;
boolean success = true;
long duration = 150L; // 毫秒

lockMonitorService.recordLockEvent(lockKey, serviceSource, operation, success, duration);
```

## 自動集成

監控服務已經與 `DistributedLockAspect` 自動集成，會自動記錄以下事件：

- **鎖獲取事件**: 當使用 `@DistributedLockable` 註解的方法嘗試獲取鎖時
- **鎖釋放事件**: 當鎖被正常釋放時
- **鎖衝突事件**: 當檢測到跨服務鎖衝突時
- **鎖超時事件**: 當鎖獲取超時時

## 配置選項

在 `application.yml` 中可以配置以下監控相關選項：

```yaml
distributed:
  lock:
    # 鎖鍵前綴
    key-prefix: "distributed:lock:storage:"
    # 是否啟用監控
    enable-monitoring: true
    # 是否啟用跨服務鎖
    cross-service-lock: true
    # 是否啟用衝突檢測
    enable-conflict-detection: true
```

## 最佳實踐

### 1. 定期檢查長時間持有的鎖
```java
@Scheduled(fixedRate = 300000) // 每5分鐘檢查一次
public void checkLongHeldLocks() {
    List<LockInfo> longHeldLocks = lockMonitorService.getLongHeldLocks(300); // 5分鐘閾值
    if (!longHeldLocks.isEmpty()) {
        logger.warn("Found {} long-held locks", longHeldLocks.size());
        // 發送告警或記錄詳細信息
    }
}
```

### 2. 監控跨服務衝突
```java
@Scheduled(fixedRate = 60000) // 每分鐘檢查一次
public void monitorCrossServiceConflicts() {
    Map<String, LockMonitorService.LockConflictInfo> conflicts = 
        lockMonitorService.detectCrossServiceConflicts();
    
    if (!conflicts.isEmpty()) {
        logger.warn("Detected {} cross-service lock conflicts", conflicts.size());
        // 實施衝突解決策略
    }
}
```

### 3. 定期重置統計信息
```java
@Scheduled(cron = "0 0 0 * * ?") // 每天午夜重置
public void resetDailyStatistics() {
    LockStatistics stats = lockMonitorService.getLockStatistics();
    logger.info("Daily lock statistics - Total: {}, Success Rate: {}%", 
               stats.getTotalLockRequests(), stats.getSuccessRate());
    
    lockMonitorService.resetStatistics();
}
```

## 故障排除

### 1. 監控服務不可用
如果監控服務不可用，檢查：
- Redis 連接是否正常
- Redisson 配置是否正確
- Spring Bean 是否正確注入

### 2. 統計數據不準確
可能的原因：
- 事件記錄被異常中斷
- 多個服務實例的統計數據未同步
- Redis 中的數據被意外清理

### 3. 性能影響
監控服務設計為低開銷，但在高併發場景下：
- 可以通過配置禁用某些監控功能
- 調整統計數據的收集頻率
- 使用異步方式記錄事件

## 擴展功能

監控服務支持以下擴展：

1. **自定義監控指標**: 實現自定義的統計收集邏輯
2. **告警集成**: 與監控系統集成，實現自動告警
3. **可視化界面**: 開發Web界面展示鎖狀態和統計信息
4. **歷史數據存儲**: 將統計數據持久化到數據庫

這個監控服務為跨服務分布式鎖提供了全面的可觀測性，幫助開發者和運維人員更好地理解和管理分布式鎖的使用情況。