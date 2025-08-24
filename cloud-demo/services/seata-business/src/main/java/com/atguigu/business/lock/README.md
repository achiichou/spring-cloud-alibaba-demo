# 分布式鎖AOP切面實現文檔

## 概述

本文檔描述了跨服務分布式鎖AOP切面的實現，該切面通過`@DistributedLockable`註解提供聲明式的分布式鎖功能，支持SpEL表達式動態生成鎖鍵，實現跨服務的鎖衝突檢測和處理。

## 核心組件

### 1. DistributedLockAspect
AOP切面類，負責攔截`@DistributedLockable`註解的方法並實現分布式鎖邏輯。

**主要功能：**
- 攔截@DistributedLockable註解的方法
- 解析SpEL表達式生成動態鎖鍵
- 實現不同的失敗處理策略
- 記錄服務來源信息到鎖上下文
- 跨服務鎖衝突檢測和處理
- 支持指數退避重試機制

### 2. @DistributedLockable註解
聲明式分布式鎖註解，支持以下屬性：

```java
@DistributedLockable(
    key = "'storage:' + #commodityCode",     // SpEL表達式鎖鍵
    waitTime = 5,                            // 等待時間（秒）
    leaseTime = 30,                          // 鎖持有時間（秒）
    failStrategy = LockFailStrategy.EXCEPTION, // 失敗策略
    businessContext = "storage-operation",    // 業務上下文
    crossService = true,                     // 是否跨服務鎖
    priority = 0                             // 鎖優先級
)
```

### 3. LockFailStrategy失敗策略
定義鎖獲取失敗時的處理方式：

- **EXCEPTION**: 拋出異常（默認）
- **RETURN_NULL**: 返回null
- **RETRY**: 使用指數退避重試
- **FALLBACK**: 執行降級邏輯
- **FAST_FAIL**: 快速失敗
- **IGNORE**: 忽略鎖保護直接執行

## SpEL表達式支持

### 基本用法
```java
// 基於方法參數
key = "'storage:' + #commodityCode"

// 基於對象屬性
key = "'user:' + #user.id"

// 組合多個參數
key = "'order:' + #orderId + ':' + #userId"
```

### 高級用法
```java
// 使用靜態方法
key = "'batch:' + T(java.util.Arrays).toString(#commodityCodes)"

// 使用日期時間
key = "'daily:' + T(java.time.LocalDate).now().toString()"

// 條件表達式
key = "#type == 'VIP' ? 'vip:' + #userId : 'normal:' + #userId"

// 集合操作
key = "'multi:' + #operations.![commodityCode].toString()"
```

### 可用的上下文變量
- 方法參數：通過參數名直接訪問
- `serviceName`: 當前服務名稱
- `methodName`: 當前方法名稱
- `className`: 當前類名稱

## 跨服務鎖機制

### 鎖鍵格式統一
兩個服務使用相同的鎖鍵格式確保互斥：
```
distributed:lock:storage:{commodityCode}
```

### 服務識別
每個鎖都記錄持有者的服務來源信息：
```java
CrossServiceLockContext {
    lockKey: "distributed:lock:storage:product001"
    serviceSource: "seata-business" // 或 "seata-storage"
    businessContext: "business-direct-deduct"
    timestamp: 1640995200000
    threadId: "http-nio-8080-exec-1"
    instanceId: "seata-business-8080-1234"
}
```

### 衝突檢測
當檢測到跨服務鎖衝突時：
1. 記錄衝突日誌
2. 可選擇等待或執行其他策略
3. 支持基於優先級的衝突解決

## 重試機制

### 指數退避算法
```java
// 計算延遲時間
long exponentialDelay = baseDelay * (1L << (attempt - 1));
long jitter = exponentialDelay * 0.25 * (random - 0.5);
long finalDelay = exponentialDelay + jitter;
```

### 配置參數
```yaml
distributed:
  lock:
    max-retry-attempts: 3      # 最大重試次數
    retry-base-delay: 100      # 基礎延遲時間（毫秒）
```

## 使用示例

### 1. 基本庫存操作
```java
@Service
public class StorageService {
    
    @DistributedLockable(
        key = "'storage:' + #commodityCode",
        waitTime = 5,
        leaseTime = 30,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "storage-deduct"
    )
    @Transactional
    public void deduct(String commodityCode, int count) {
        // 庫存扣減邏輯
        storageTblMapper.deduct(commodityCode, count);
    }
}
```

### 2. 批量操作
```java
@DistributedLockable(
    key = "'batch:' + T(java.util.Arrays).toString(#operations.![commodityCode].toArray())",
    waitTime = 10,
    leaseTime = 60,
    failStrategy = LockFailStrategy.EXCEPTION,
    businessContext = "batch-operation"
)
public void batchOperation(List<StorageOperation> operations) {
    // 批量處理邏輯
}
```

### 3. 可選操作（返回null策略）
```java
@DistributedLockable(
    key = "'optional:' + #resourceId",
    waitTime = 2,
    leaseTime = 15,
    failStrategy = LockFailStrategy.RETURN_NULL
)
public String optionalOperation(String resourceId) {
    // 可選的業務邏輯
    return "操作結果";
}
```

### 4. 高可用操作（降級策略）
```java
@DistributedLockable(
    key = "'ha:' + #operationId",
    waitTime = 1,
    leaseTime = 10,
    failStrategy = LockFailStrategy.FALLBACK
)
public String highAvailabilityOperation(String operationId) {
    // 高可用性業務邏輯
    return "操作結果";
}
```

## 配置說明

### application.yml配置
```yaml
spring:
  application:
    name: seata-business  # 服務名稱，用於鎖上下文識別

distributed:
  lock:
    enable-conflict-detection: true    # 啟用跨服務衝突檢測
    max-retry-attempts: 3              # 最大重試次數
    retry-base-delay: 100              # 基礎重試延遲（毫秒）
    default-wait-time: 5               # 默認等待時間（秒）
    default-lease-time: 30             # 默認鎖持有時間（秒）

# Redis配置（Redisson）
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 3000ms
```

### Maven依賴
```xml
<!-- AOP支持 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Redis和Redisson -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.24.3</version>
</dependency>
```

## 監控和日誌

### 日誌級別
```yaml
logging:
  level:
    com.atguigu.business.lock.DistributedLockAspect: INFO
    com.atguigu.business.lock.RedisDistributedLock: INFO
```

### 關鍵日誌信息
- 鎖獲取成功/失敗
- 跨服務鎖衝突檢測
- 重試過程記錄
- 鎖釋放確認
- 異常情況處理

### 監控指標
- 鎖獲取成功率
- 平均等待時間
- 跨服務衝突次數
- 重試成功率
- 鎖持有時間分布

## 最佳實踐

### 1. 鎖鍵設計
- 使用有意義的前綴區分不同業務
- 保持鎖鍵簡潔但具有唯一性
- 避免使用過長的鎖鍵

### 2. 超時時間設置
- `waitTime`: 根據業務容忍度設置，通常1-10秒
- `leaseTime`: 根據業務處理時間設置，留有餘量

### 3. 失敗策略選擇
- 關鍵業務：使用EXCEPTION
- 可選功能：使用RETURN_NULL或IGNORE
- 高可用場景：使用FALLBACK
- 可重試場景：使用RETRY

### 4. 性能優化
- 合理設置重試參數避免過度重試
- 使用批量鎖減少鎖競爭
- 監控鎖持有時間，優化業務邏輯

### 5. 故障處理
- 實現Redis連接異常的降級策略
- 設置合理的鎖超時時間防止死鎖
- 定期清理過期的鎖信息

## 故障排除

### 常見問題

1. **鎖獲取超時**
   - 檢查Redis連接狀態
   - 確認鎖持有時間設置是否合理
   - 檢查是否存在死鎖情況

2. **SpEL表達式解析失敗**
   - 確認方法參數名稱正確
   - 檢查表達式語法
   - 驗證對象屬性是否存在

3. **跨服務鎖衝突**
   - 確認兩個服務使用相同的鎖鍵格式
   - 檢查Redis實例是否一致
   - 驗證服務名稱配置

4. **AOP切面不生效**
   - 確認已添加spring-boot-starter-aop依賴
   - 檢查@EnableAspectJAutoProxy配置
   - 驗證方法是否為public且通過Spring代理調用

### 調試技巧
- 啟用DEBUG日誌查看詳細執行過程
- 使用Redis客戶端工具查看鎖狀態
- 通過JMX監控鎖相關指標
- 使用分布式追蹤工具跟蹤跨服務調用

## 擴展功能

### 自定義失敗策略
可以通過實現自定義邏輯擴展失敗處理策略：

```java
// 在DistributedLockAspect中擴展
private Object executeCustomFallback(ProceedingJoinPoint joinPoint, String lockKey) {
    // 自定義降級邏輯
    return customFallbackService.handleLockFailure(lockKey);
}
```

### 鎖優先級支持
可以基於業務優先級實現鎖的優先獲取：

```java
@DistributedLockable(
    key = "'priority:' + #taskId",
    priority = 1  // 高優先級
)
public void highPriorityTask(String taskId) {
    // 高優先級任務
}
```

### 動態配置支持
支持運行時動態調整鎖參數：

```java
@Value("${distributed.lock.dynamic.wait-time:5}")
private long dynamicWaitTime;
```

這個分布式鎖AOP切面實現提供了完整的跨服務分布式鎖解決方案，支持多種使用場景和配置選項，具有良好的擴展性和可維護性。