# Seata全局事務與分布式鎖集成說明

## 概述

本文檔說明了分布式鎖與Seata全局事務的集成實現，確保分布式鎖與事務生命週期保持同步。

## 核心組件

### 1. SeataGlobalLockTransactionSynchronization

事務同步器，實現了Spring的`TransactionSynchronization`接口，負責：

- **鎖註冊**：將分布式鎖註冊到當前全局事務
- **事務提交**：全局事務提交後釋放所有註冊的鎖
- **事務回滾**：全局事務回滾後立即釋放所有註冊的鎖
- **事務掛起/恢復**：處理事務掛起和恢復時的鎖狀態
- **監控統計**：記錄事務鎖相關的統計信息

### 2. DistributedLockAspect 增強

分布式鎖切面已增強以支持Seata事務集成：

- **自動註冊**：成功獲取鎖後自動註冊到當前全局事務
- **智能釋放**：根據事務狀態決定是否立即釋放鎖
- **事務感知**：檢測當前是否在全局事務中執行

## 工作流程

### 正常事務提交流程

```
1. 開始全局事務 (@GlobalTransactional)
2. 執行業務方法 (@DistributedLockable)
3. 獲取分布式鎖
4. 註冊鎖到事務同步器
5. 執行業務邏輯
6. 全局事務準備提交
7. 事務同步器釋放所有鎖
8. 全局事務提交完成
```

### 事務回滾流程

```
1. 開始全局事務 (@GlobalTransactional)
2. 執行業務方法 (@DistributedLockable)
3. 獲取分布式鎖
4. 註冊鎖到事務同步器
5. 執行業務邏輯（發生異常）
6. 全局事務回滾
7. 事務同步器立即釋放所有鎖
8. 全局事務回滾完成
```

## 配置說明

### application.yml 配置

```yaml
distributed:
  lock:
    seata:
      auto-release: true # 事務結束時自動釋放鎖
      release-timeout: 5000 # 鎖釋放超時時間（毫秒）
```

### 註解使用

```java
@Service
public class BusinessServiceImpl {
    
    @GlobalTransactional
    @DistributedLockable(
        key = "'purchase:' + #commodityCode",
        waitTime = 10,
        leaseTime = 60,
        businessContext = "global-transaction-purchase"
    )
    public void purchase(String userId, String commodityCode, int orderCount) {
        // 業務邏輯
        // 鎖會在事務提交/回滾時自動釋放
    }
}
```

## 關鍵特性

### 1. 自動生命週期管理

- **自動註冊**：鎖獲取成功後自動註冊到事務
- **自動釋放**：事務結束時自動釋放鎖
- **異常安全**：即使發生異常也能正確釋放鎖

### 2. 事務狀態感知

- **提交後釋放**：事務提交成功後釋放鎖
- **回滾立即釋放**：事務回滾時立即釋放鎖
- **超時處理**：事務超時時配合鎖超時機制

### 3. 監控和統計

- **事務鎖事件**：記錄鎖的註冊、釋放等事件
- **持有時間統計**：統計鎖在事務中的持有時間
- **批量釋放統計**：記錄批量釋放鎖的性能數據

### 4. 跨服務支持

- **服務標識**：記錄鎖的服務來源
- **衝突檢測**：檢測跨服務鎖衝突
- **統一管理**：支持跨服務的鎖統一管理

## 使用場景

### 1. 標準全局事務

```java
@GlobalTransactional
@DistributedLockable(key = "'order:' + #orderId")
public void processOrder(String orderId) {
    // 1. 扣減庫存
    storageService.deduct(commodityCode, count);
    // 2. 創建訂單
    orderService.create(orderId, commodityCode, count);
    // 鎖會在事務提交後自動釋放
}
```

### 2. 直接數據庫操作

```java
@GlobalTransactional
public void purchaseWithDirectStorage(String userId, String commodityCode, int count) {
    // 直接操作數據庫的方法已經有分布式鎖保護
    businessStorageService.directDeduct(commodityCode, count, "purchase");
    orderService.create(userId, commodityCode, count);
    // 所有鎖會在事務結束時統一釋放
}
```

### 3. 批量操作

```java
@GlobalTransactional
public void batchProcess(List<Operation> operations) {
    // 批量操作方法已經有分布式鎖保護
    businessStorageService.batchStorageOperation(operations);
    // 批量鎖會在事務結束時統一釋放
}
```

## 測試驗證

### 測試接口

提供了專門的測試控制器 `SeataTransactionTestController`：

- `POST /api/seata-transaction-test/purchase` - 測試標準購買流程
- `POST /api/seata-transaction-test/purchase-direct` - 測試直接庫存操作
- `POST /api/seata-transaction-test/test-rollback` - 測試事務回滾場景

### 測試場景

1. **正常提交**：驗證事務提交後鎖正確釋放
2. **異常回滾**：驗證事務回滾後鎖立即釋放
3. **併發測試**：驗證多個事務併發執行時的鎖行為
4. **跨服務測試**：驗證跨服務場景下的鎖同步

## 監控指標

### 事務鎖指標

- `distributed.lock.transaction.hold.duration` - 事務鎖持有時間
- `distributed.lock.batch.release.duration` - 批量釋放耗時
- `distributed.lock.lost` - 鎖丟失次數

### 統計信息

- 活躍事務鎖數量
- 平均事務鎖持有時間
- 事務鎖釋放成功率
- 跨服務事務鎖衝突率

## 注意事項

### 1. 性能考慮

- 事務鎖會增加事務的持有時間
- 建議合理設置鎖的超時時間
- 避免在長事務中持有過多鎖

### 2. 異常處理

- 確保業務異常能正確觸發事務回滾
- 監控鎖釋放失敗的情況
- 設置合理的鎖釋放超時時間

### 3. 配置優化

- 根據業務場景調整鎖的等待時間和持有時間
- 啟用監控以便及時發現問題
- 配置合適的Redis連接池大小

## 故障排除

### 常見問題

1. **鎖未釋放**
   - 檢查事務是否正常提交/回滾
   - 查看事務同步器是否正常工作
   - 檢查Redis連接是否正常

2. **性能問題**
   - 檢查鎖的持有時間是否過長
   - 查看是否有鎖競爭激烈的情況
   - 優化業務邏輯減少鎖持有時間

3. **跨服務衝突**
   - 檢查服務間的鎖鍵是否衝突
   - 查看跨服務鎖衝突統計
   - 調整鎖的優先級或等待時間

### 日誌分析

關鍵日誌關鍵字：
- `SeataGlobalLockTransactionSynchronization`
- `Registered distributed lock`
- `Transaction committed/rolled back`
- `Released distributed locks`