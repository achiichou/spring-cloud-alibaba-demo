# 本地事務與分布式鎖集成

## 概述

`LocalLockTransactionSynchronization` 類實現了分布式鎖與本地事務生命週期的同步，確保在 seata-storage 服務中，分布式鎖能夠與本地事務狀態保持一致。

## 核心功能

### 1. 事務鎖註冊
- 當方法被 `@DistributedLockable` 註解標記時，鎖會自動註冊到當前本地事務
- 只有在活躍事務中才會進行註冊
- 支持多個鎖在同一事務中註冊

### 2. 事務生命週期同步
- **事務提交後**：自動釋放所有註冊的鎖
- **事務回滾後**：立即釋放所有註冊的鎖
- **事務掛起/恢復**：保持鎖狀態，驗證鎖的有效性

### 3. 錯誤處理
- 檢測鎖丟失情況並記錄指標
- 支持強制釋放鎖的管理功能
- 提供詳細的日誌記錄

## 使用方式

### 1. 自動集成
在 `StorageServiceImpl` 中使用 `@DistributedLockable` 註解：

```java
@Service
public class StorageServiceImpl implements StorageService {
    
    @Override
    @DistributedLockable(
        key = "'storage:' + #commodityCode", 
        waitTime = 5, 
        leaseTime = 30,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "storage-deduct"
    )
    @Transactional(rollbackFor = Exception.class)
    public void deduct(String commodityCode, int count) {
        // 業務邏輯
        storageTblMapper.deduct(commodityCode, count);
        
        // 如果發生異常，事務回滾時會自動釋放鎖
        if (Objects.equals(5, count)) {
            throw new RuntimeException("庫存不足！");
        }
    }
}
```

### 2. 配置參數
在 `application.yml` 中配置：

```yaml
distributed:
  lock:
    local:
      auto-release: true # 本地事務結束時自動釋放鎖
      release-timeout: 5000 # 鎖釋放超時時間（毫秒）
```

## 工作流程

### 正常提交流程
1. 方法執行前：獲取分布式鎖
2. 鎖註冊：將鎖註冊到當前事務的同步器
3. 業務執行：執行實際的業務邏輯
4. 事務提交：`afterCommit()` 被調用，釋放所有註冊的鎖
5. 清理：清理線程本地存儲

### 異常回滾流程
1. 方法執行前：獲取分布式鎖
2. 鎖註冊：將鎖註冊到當前事務的同步器
3. 業務執行：執行業務邏輯時發生異常
4. 事務回滾：`afterCompletion(STATUS_ROLLED_BACK)` 被調用
5. 立即釋放：立即釋放所有註冊的鎖
6. 清理：清理線程本地存儲

## 監控和指標

### 1. 事務鎖持有時間
```java
metricsCollector.recordTransactionLockHoldTime(lockKey, serviceName, 
    transactionName, Duration.ofMillis(holdTime));
```

### 2. 鎖丟失事件
```java
metricsCollector.recordLockLost(lockKey, serviceName, transactionName);
```

### 3. 批量鎖釋放
```java
metricsCollector.recordBatchLockRelease(serviceName, transactionName, reason, 
    successCount, failureCount, Duration.ofMillis(totalTime));
```

## 與 Seata 全局事務的區別

| 特性 | LocalLockTransactionSynchronization | SeataGlobalLockTransactionSynchronization |
|------|-----------------------------------|------------------------------------------|
| 適用場景 | seata-storage 本地事務 | seata-business 全局事務 |
| 事務ID | 本地事務名稱 | Seata XID |
| 事務管理器 | Spring TransactionSynchronizationManager | Seata RootContext |
| 釋放時機 | 本地事務提交/回滾後 | 全局事務提交/回滾後 |

## 最佳實踐

### 1. 事務邊界
- 確保 `@Transactional` 和 `@DistributedLockable` 在同一個方法上
- 避免在事務外部手動釋放鎖

### 2. 異常處理
- 使用 `@Transactional(rollbackFor = Exception.class)` 確保所有異常都觸發回滾
- 在業務邏輯中適當處理異常，避免鎖長時間持有

### 3. 性能考慮
- 合理設置 `waitTime` 和 `leaseTime`
- 監控鎖持有時間，避免長事務

### 4. 調試和故障排除
- 啟用詳細日誌記錄
- 使用 Spring Boot Actuator 監控鎖指標
- 定期檢查鎖統計信息

## 故障排除

### 1. 鎖未釋放
- 檢查事務是否正常提交或回滾
- 確認 `auto-release` 配置為 `true`
- 查看日誌中的鎖釋放記錄

### 2. 鎖丟失
- 檢查 Redis 連接穩定性
- 確認鎖的 `leaseTime` 設置合理
- 監控 `recordLockLost` 指標

### 3. 性能問題
- 檢查鎖競爭情況
- 優化事務執行時間
- 調整鎖的等待和持有時間

## 測試

### 1. 單元測試
- `LocalLockTransactionSynchronizationTest`：測試核心功能
- 模擬各種事務狀態和異常情況

### 2. 集成測試
- `LocalTransactionIntegrationTest`：測試與 Spring 事務的集成
- 驗證實際業務場景中的行為

### 3. 性能測試
- 併發事務測試
- 鎖競爭場景測試
- 長時間運行穩定性測試