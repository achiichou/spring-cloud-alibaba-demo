# 跨服務分布式鎖開發者指南

## 概述

本文檔詳細介紹了在seata-business和seata-storage服務中實現的跨服務分布式鎖系統的使用方法、配置選項和最佳實踐。

## 系統架構

### 核心組件

1. **RedisDistributedLock** - 基於Redisson的分布式鎖實現
2. **DistributedLockAspect** - 基於註解的AOP切面
3. **LockMonitorService** - 鎖監控和管理服務
4. **CrossServiceLockKeyGenerator** - 跨服務鎖鍵生成器
5. **DistributedLockHealthIndicator** - 健康檢查指標

### 服務角色

- **seata-business**: 業務協調服務，可直接操作storage_db，使用全局事務
- **seata-storage**: 庫存服務，標準的庫存操作服務，使用本地事務

## 快速開始

### 1. 基本使用

在需要分布式鎖保護的方法上添加`@DistributedLockable`註解：

```java
@Service
public class BusinessStorageServiceImpl implements BusinessStorageService {
    
    @Override
    @DistributedLockable(key = "'storage:' + #commodityCode", waitTime = 5, leaseTime = 30)
    @Transactional(rollbackFor = Exception.class)
    public void directDeduct(String commodityCode, int count, String businessContext) {
        // 業務邏輯
        storageTblMapper.deduct(commodityCode, count);
    }
}
```

### 2. 高級用法

#### 批量操作鎖

```java
@DistributedLockable(
    key = "'batch:' + T(java.util.Arrays).toString(#operations.![commodityCode].toArray())",
    waitTime = 10,
    leaseTime = 60,
    failStrategy = LockFailStrategy.RETRY,
    businessContext = "批量庫存操作"
)
public void batchStorageOperation(List<StorageOperation> operations) {
    // 批量業務邏輯
}
```

#### 優先級鎖

```java
@DistributedLockable(
    key = "'storage:' + #commodityCode",
    priority = 1,  // 數值越小優先級越高
    businessContext = "高優先級操作"
)
public void highPriorityOperation(String commodityCode) {
    // 高優先級業務邏輯
}
```

## 配置詳解

### Redis配置

```yaml
distributed:
  lock:
    redis:
      host: localhost          # Redis服務器地址
      port: 6379              # Redis服務器端口
      password:               # Redis密碼（可選）
      database: 0             # Redis數據庫編號
      timeout: 3000           # 連接超時時間（毫秒）
      connection-pool-size: 10 # 連接池大小
      connection-minimum-idle-size: 5 # 最小空閒連接數
```

**重要提醒**：兩個服務必須連接到相同的Redis實例才能實現跨服務互斥。

### 鎖行為配置

```yaml
distributed:
  lock:
    lock:
      default-wait-time: 5     # 默認等待時間（秒）
      default-lease-time: 30   # 默認持有時間（秒）
      key-prefix: "distributed:lock:storage:" # 鎖鍵前綴
      enable-monitoring: true  # 啟用監控
      cross-service-lock: true # 跨服務鎖標識
      service-identifier: "seata-business" # 服務標識符
```

### 錯誤處理配置

```yaml
distributed:
  lock:
    max-retry-attempts: 3           # 最大重試次數
    retry-base-delay: 100           # 重試基礎延遲（毫秒）
    enable-degradation: true        # 啟用降級模式
    circuit-breaker-threshold: 5    # 熔斷器閾值
```

## 監控和管理

### 健康檢查

系統提供了健康檢查端點：

```bash
# 檢查分布式鎖系統健康狀態
curl http://localhost:11000/actuator/health

# 檢查自定義分布式鎖端點
curl http://localhost:11000/actuator/distributed-lock
```

健康檢查包含以下信息：
- Redis連接狀態
- 熔斷器狀態
- 當前鎖數量
- 長期持有的鎖警告
- 鎖成功率統計

### 監控指標

系統暴露了以下Prometheus指標：

```
# 鎖獲取嘗試次數
distributed.lock.acquisition.attempts

# 鎖獲取成功次數
distributed.lock.acquisition.success

# 鎖獲取失敗次數
distributed.lock.acquisition.failures

# 熔斷器觸發次數
distributed.lock.circuit.breaker.triggers

# 當前活躍鎖數量
distributed.lock.current.count

# Redis健康狀態
distributed.lock.redis.healthy

# 熔斷器狀態
distributed.lock.circuit.breaker.open
```

### 管理API

seata-business服務提供了鎖管理REST API：

```bash
# 獲取所有鎖狀態
GET /business/lock/status

# 獲取鎖統計信息
GET /business/lock/statistics

# 強制釋放指定鎖
DELETE /business/lock/force-unlock?lockKey=distributed:lock:storage:PRODUCT_001

# 重置熔斷器
POST /business/lock/reset-circuit-breaker
```

## 最佳實踐

### 1. 鎖粒度設計

**推薦**：基於業務實體的細粒度鎖
```java
@DistributedLockable(key = "'storage:' + #commodityCode")
```

**避免**：過於寬泛的鎖
```java
@DistributedLockable(key = "'storage:all'")  // 不推薦
```

### 2. 超時時間設置

- **等待時間**：根據業務特性設置，一般3-10秒
- **持有時間**：預估業務執行時間的2-3倍，防止死鎖

```java
@DistributedLockable(
    key = "'storage:' + #commodityCode",
    waitTime = 5,    // 等待5秒
    leaseTime = 30   // 持有30秒
)
```

### 3. 事務配置

確保分布式鎖與事務邊界對齊：

```java
@DistributedLockable(key = "'storage:' + #commodityCode")
@Transactional(rollbackFor = Exception.class)
public void businessMethod(String commodityCode) {
    // 業務邏輯
}
```

### 4. 異常處理

使用適當的失敗策略：

```java
@DistributedLockable(
    key = "'storage:' + #commodityCode",
    failStrategy = LockFailStrategy.RETRY  // 或 EXCEPTION, RETURN_NULL, FALLBACK
)
```

### 5. 業務上下文

提供有意義的業務上下文便於監控和調試：

```java
@DistributedLockable(
    key = "'storage:' + #commodityCode",
    businessContext = "訂單庫存扣減操作"
)
```

## 故障排除

### 常見問題

#### 1. 鎖獲取超時

**症狀**：方法執行時拋出`DistributedLockException`，錯誤碼為`LOCK_ACQUIRE_TIMEOUT`

**可能原因**：
- Redis連接不穩定
- 其他服務長期持有鎖
- 併發量過高

**解決方案**：
- 檢查Redis連接狀態
- 檢查鎖監控API，確認是否有長期持有的鎖
- 適當調整`waitTime`參數
- 考慮使用鎖管理API強制釋放異常鎖

#### 2. 熔斷器開啟

**症狀**：日誌中出現"Circuit breaker is open"

**原因**：連續失敗次數超過閾值

**解決方案**：
```bash
# 檢查熔斷器狀態
curl http://localhost:11000/actuator/health

# 手動重置熔斷器
curl -X POST http://localhost:11000/actuator/distributed-lock/reset-circuit-breaker
```

#### 3. 跨服務鎖衝突

**症狀**：一個服務無法獲取鎖，另一個服務長期持有

**排查步驟**：
1. 檢查兩個服務的Redis配置是否一致
2. 檢查鎖鍵前綴配置是否相同
3. 查看鎖管理API獲取詳細信息

#### 4. 降級模式觸發

**症狀**：日誌中出現"Using degraded lock mode"

**說明**：Redis不可用時觸發本地鎖降級

**注意**：降級模式只能保證單個服務實例內的互斥，不能保證跨服務互斥

### 監控告警建議

設置以下告警規則：

1. **Redis連接異常**：`distributed.lock.redis.healthy == 0`
2. **熔斷器開啟**：`distributed.lock.circuit.breaker.open == 1`
3. **鎖成功率過低**：鎖成功率 < 95%
4. **長期持有鎖**：單個鎖持有時間 > 5分鐘

## 性能考慮

### 併發能力

- 單個Redis實例理論支持10萬+併發鎖操作
- 實際性能取決於網絡延遲和業務複雜度
- 建議進行壓力測試確定系統容量

### 優化建議

1. **連接池調優**：根據併發需求調整連接池大小
2. **超時時間優化**：避免過長的等待時間
3. **鎖粒度優化**：使用細粒度鎖減少衝突
4. **批量操作**：對於批量業務考慮使用批量鎖

## 升級指南

### 版本兼容性

- 鎖鍵格式向後兼容
- 配置項支持默認值，漸進式升級
- 監控指標保持向後兼容

### 滾動升級

支持零停機滾動升級：

1. 升級seata-storage服務
2. 驗證跨服務功能正常
3. 升級seata-business服務
4. 全面驗證系統功能

## 相關資源

- [分布式鎖設計文檔](../.kiro/specs/distributed-lock/design.md)
- [需求規格說明](../.kiro/specs/distributed-lock/requirements.md)
- [測試策略文檔](src/test/java/com/atguigu/business/lock/TEST_SUMMARY.md)
- [性能測試報告](src/test/java/com/atguigu/business/performance/PERFORMANCE_TEST_SUMMARY.md)