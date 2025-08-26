# 跨服務分布式鎖故障排除和最佳實踐指南

## 概述

本指南提供了跨服務分布式鎖系統的故障排除方法、最佳實踐和運維建議，幫助開發者和運維人員快速定位和解決問題。

## 快速診斷檢查清單

### 環境檢查

- [ ] Redis服務正常運行且可連接
- [ ] 兩個服務（seata-business和seata-storage）都已啟動
- [ ] 兩個服務連接到相同的Redis實例
- [ ] 網絡連接正常，無防火牆阻攔

### 配置檢查

- [ ] 鎖鍵前綴（key-prefix）在兩個服務中完全一致
- [ ] Redis連接配置（host、port、database）一致
- [ ] 服務標識符（service-identifier）在兩個服務中不同但已正確設置

### 功能檢查

```bash
# 檢查seata-business健康狀態
curl http://localhost:11000/actuator/health

# 檢查seata-storage健康狀態  
curl http://localhost:13000/actuator/health

# 檢查分布式鎖狀態
curl http://localhost:11000/actuator/distributed-lock
```

## 常見故障場景

### 1. 鎖獲取超時

#### 症狀
```
DistributedLockException: Failed to acquire lock after waiting 5 seconds
Error Code: LOCK_ACQUIRE_TIMEOUT
```

#### 診斷步驟

1. **檢查當前鎖狀態**
   ```bash
   curl http://localhost:11000/business/lock/status
   ```

2. **檢查長期持有的鎖**
   ```bash
   # 查看詳細鎖信息
   curl http://localhost:11000/business/lock/statistics
   ```

3. **檢查Redis連接**
   ```bash
   # 連接Redis檢查鎖鍵
   redis-cli
   127.0.0.1:6379> keys distributed:lock:storage:*
   ```

#### 解決方案

**臨時解決**：
```bash
# 強制釋放特定鎖
curl -X DELETE "http://localhost:11000/business/lock/force-unlock?lockKey=distributed:lock:storage:PRODUCT_001"
```

**根本解決**：
- 檢查業務邏輯是否有長期運行的操作
- 調整鎖的`leaseTime`參數
- 優化業務邏輯執行時間
- 檢查是否有未正常釋放的鎖

### 2. 跨服務鎖衝突

#### 症狀
- 一個服務可以獲取鎖，另一個服務始終獲取失敗
- 日誌中出現跨服務衝突記錄

#### 診斷步驟

1. **驗證配置一致性**
   ```bash
   # 比較兩個服務的配置
   diff seata-business/application.yml seata-storage/application.yml
   ```

2. **檢查Redis連接**
   ```bash
   # 在兩個服務中分別測試Redis連接
   # seata-business
   curl http://localhost:11000/actuator/health | jq '.components.redis'
   
   # seata-storage  
   curl http://localhost:13000/actuator/health | jq '.components.redis'
   ```

3. **檢查鎖鍵生成**
   ```java
   // 在日誌中查找鎖鍵生成記錄
   grep "Successfully acquired distributed lock" logs/seata-business.log
   grep "Successfully acquired distributed lock" logs/seata-storage.log
   ```

#### 解決方案

1. **確保配置一致**
   ```yaml
   distributed:
     lock:
       lock:
         key-prefix: "distributed:lock:storage:"  # 必須完全一致
   ```

2. **驗證鎖鍵生成邏輯**
   ```java
   // 確保SpEL表達式在兩個服務中產生相同的鎖鍵
   @DistributedLockable(key = "'storage:' + #commodityCode")
   ```

### 3. 熔斷器誤觸發

#### 症狀
```
Circuit breaker is open, using degradation mode for lock
```

#### 診斷步驟

1. **檢查熔斷器狀態**
   ```bash
   curl http://localhost:11000/actuator/distributed-lock | jq '.circuitBreaker'
   ```

2. **檢查失敗原因**
   ```bash
   # 查看錯誤日誌
   tail -f logs/seata-business.log | grep -i error
   ```

#### 解決方案

1. **臨時重置熔斷器**
   ```bash
   curl -X POST http://localhost:11000/actuator/distributed-lock/reset-circuit-breaker
   ```

2. **調整熔斷器閾值**
   ```yaml
   distributed:
     lock:
       circuit-breaker-threshold: 10  # 增加閾值
   ```

### 4. 降級模式不當觸發

#### 症狀
```
Using degraded lock mode for key: distributed:lock:storage:PRODUCT_001
Acquired degraded lock (local only): distributed:lock:storage:PRODUCT_001
```

#### 風險
降級模式只能保證單個服務實例內的互斥，無法保證跨服務互斥，可能導致數據不一致。

#### 診斷步驟

1. **檢查Redis可用性**
   ```bash
   redis-cli ping
   ```

2. **檢查網絡連接**
   ```bash
   telnet localhost 6379
   ```

#### 解決方案

1. **恢復Redis服務**
2. **檢查網絡配置**
3. **考慮禁用降級模式**（如果數據一致性要求很高）
   ```yaml
   distributed:
     lock:
       enable-degradation: false
   ```

## 性能調優

### 1. 連接池優化

```yaml
distributed:
  lock:
    redis:
      connection-pool-size: 20           # 根據併發量調整
      connection-minimum-idle-size: 10   # 保持足夠的空閒連接
```

### 2. 重試策略優化

```yaml
distributed:
  lock:
    max-retry-attempts: 3      # 平衡重試次數和響應時間
    retry-base-delay: 100      # 基礎延遲，實際使用指數退避
```

### 3. 超時時間調優

- **短期操作**：waitTime=3s, leaseTime=10s
- **中期操作**：waitTime=5s, leaseTime=30s  
- **長期操作**：waitTime=10s, leaseTime=60s

## 監控和告警

### 關鍵指標監控

1. **鎖成功率**
   ```promql
   distributed_lock_acquisition_success / distributed_lock_acquisition_attempts * 100
   ```

2. **平均鎖等待時間**
   ```promql
   distributed_lock_wait_time_seconds_sum / distributed_lock_wait_time_seconds_count
   ```

3. **當前活躍鎖數量**
   ```promql
   distributed_lock_current_count
   ```

### 告警規則建議

```yaml
groups:
- name: distributed-lock
  rules:
  - alert: DistributedLockHighFailureRate
    expr: distributed_lock_acquisition_success / distributed_lock_acquisition_attempts * 100 < 95
    for: 5m
    annotations:
      summary: "分布式鎖失敗率過高"
      
  - alert: DistributedLockCircuitBreakerOpen
    expr: distributed_lock_circuit_breaker_open == 1
    for: 1m
    annotations:
      summary: "分布式鎖熔斷器開啟"
      
  - alert: DistributedLockRedisUnhealthy
    expr: distributed_lock_redis_healthy == 0
    for: 2m
    annotations:
      summary: "分布式鎖Redis連接異常"
```

## 運維最佳實踐

### 1. 部署建議

- **Redis高可用**：使用Redis Cluster或Sentinel模式
- **服務部署**：支持滾動升級，確保零停機
- **監控覆蓋**：所有關鍵指標都有監控和告警

### 2. 測試策略

```bash
# 運行跨服務集成測試
mvn test -Dtest=CrossServiceStorageIntegrationTest

# 運行端到端測試  
mvn test -Dtest=EndToEndCrossServiceTest

# 運行性能測試
mvn test -Dtest=CrossServiceConcurrentTest
```

### 3. 容量規劃

- **併發能力**：單Redis實例支持10,000+併發鎖操作
- **響應時間**：正常情況下鎖獲取 < 50ms
- **資源使用**：監控Redis內存使用，避免OOM

### 4. 災難恢復

#### Redis故障恢復

1. **主從切換**：自動故障轉移到從節點
2. **數據恢復**：從RDB/AOF文件恢復
3. **服務重啟**：服務會自動重連新的Redis實例

#### 服務故障恢復

1. **實例重啟**：鎖會自動超時釋放
2. **數據檢查**：驗證業務數據一致性
3. **手動介入**：必要時手動釋放異常鎖

## 開發建議

### 1. 代碼層面

```java
// 好的實踐
@DistributedLockable(
    key = "'storage:' + #commodityCode",
    waitTime = 5,
    leaseTime = 30,
    businessContext = "庫存扣減操作",
    failStrategy = LockFailStrategy.EXCEPTION
)
@Transactional(rollbackFor = Exception.class)
public void deductStock(String commodityCode, int count) {
    // 業務邏輯
}

// 避免的做法
@DistributedLockable(key = "'global-lock'")  // 鎖粒度過大
public void processAll() {
    // 影響所有操作
}
```

### 2. 測試建議

- **單元測試**：模擬Redis異常情況
- **集成測試**：測試跨服務協作
- **性能測試**：驗證併發性能
- **混沌測試**：模擬各種故障場景

### 3. 日誌規範

```java
// 使用結構化日誌
log.info("Lock acquisition attempt - service: {}, key: {}, thread: {}", 
         serviceName, lockKey, Thread.currentThread().getName());

// 錯誤日誌包含上下文
log.error("Lock acquisition failed - service: {}, key: {}, error: {}, attempt: {}/{}", 
          serviceName, lockKey, e.getMessage(), attempt, maxAttempts);
```

## FAQ

### Q1: 兩個服務必須同時重啟嗎？
A: 不需要。系統支持滾動升級，可以逐個重啟服務。

### Q2: Redis宕機會導致業務中斷嗎？
A: 如果啟用降級模式，業務可以繼續運行但失去跨服務互斥保證。建議使用Redis高可用方案。

### Q3: 如何處理鎖永不釋放的情況？
A: 鎖有自動超時機制（leaseTime），超時後自動釋放。也可以使用管理API手動釋放。

### Q4: 性能瓶頸在哪裡？
A: 主要瓶頸通常是：
- Redis網絡延遲
- 業務邏輯執行時間
- 鎖競爭激烈程度

### Q5: 如何保證配置一致性？
A: 建議使用配置中心（如Nacos）統一管理配置，或者使用配置檢查腳本驗證一致性。

## 聯繫和支援

- **技術文檔**：查看`DISTRIBUTED_LOCK_DEVELOPER_GUIDE.md`
- **設計文檔**：查看`.kiro/specs/distributed-lock/design.md`
- **測試報告**：查看測試目錄下的相關文檔