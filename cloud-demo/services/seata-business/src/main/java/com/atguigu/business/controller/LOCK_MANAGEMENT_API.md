# 跨服務分布式鎖管理 REST API 文檔

## 概述

本文檔描述了 seata-business 服務中實現的跨服務分布式鎖管理 REST API 接口。這些接口提供了鎖狀態查詢、統計信息獲取、強制釋放和衝突檢測等管理功能。

## 基礎路徑

所有 API 接口的基礎路徑為：`/api/lock-management`

## API 接口列表

### 1. 獲取所有鎖狀態

**接口地址：** `GET /api/lock-management/locks`

**描述：** 獲取當前所有分布式鎖的狀態信息

**請求參數：**
- `serviceSource` (可選): 服務來源過濾器，支持 `seata-business` 或 `seata-storage`

**響應示例：**
```json
{
  "success": true,
  "message": "成功獲取所有 5 個鎖信息",
  "data": [
    {
      "lockKey": "distributed:lock:storage:PRODUCT001",
      "holder": "seata-business-instance-1",
      "serviceSource": "seata-business",
      "acquireTime": "2024-01-01T10:30:00",
      "leaseTime": 30,
      "remainingTime": 25,
      "businessContext": "business-direct-deduct",
      "status": "ACTIVE",
      "lockType": "STORAGE_DEDUCT",
      "holdDuration": 5,
      "expired": false
    }
  ],
  "timestamp": "2024-01-01T10:30:05"
}
```

### 2. 獲取指定鎖信息

**接口地址：** `GET /api/lock-management/locks/{lockKey}`

**描述：** 獲取指定鎖鍵的詳細信息

**路徑參數：**
- `lockKey`: 鎖鍵

**響應示例：**
```json
{
  "success": true,
  "message": "成功獲取鎖信息",
  "data": {
    "lockKey": "distributed:lock:storage:PRODUCT001",
    "holder": "seata-business-instance-1",
    "serviceSource": "seata-business",
    "acquireTime": "2024-01-01T10:30:00",
    "leaseTime": 30,
    "remainingTime": 25,
    "businessContext": "business-direct-deduct",
    "status": "ACTIVE",
    "lockType": "STORAGE_DEDUCT",
    "holdDuration": 5,
    "expired": false
  },
  "timestamp": "2024-01-01T10:30:05"
}
```

### 3. 獲取鎖統計信息

**接口地址：** `GET /api/lock-management/statistics`

**描述：** 獲取跨服務分布式鎖的統計信息

**請求參數：**
- `startTime` (可選): 開始時間（毫秒時間戳）
- `endTime` (可選): 結束時間（毫秒時間戳）

**響應示例：**
```json
{
  "success": true,
  "message": "成功獲取鎖統計信息",
  "data": {
    "statisticsStartTime": "2024-01-01T09:00:00",
    "statisticsEndTime": "2024-01-01T10:30:00",
    "totalLockRequests": 1000,
    "successfulLocks": 950,
    "failedLocks": 50,
    "timeoutLocks": 10,
    "averageWaitTime": 50,
    "averageHoldTime": 2000,
    "maxWaitTime": 500,
    "maxHoldTime": 30000,
    "crossServiceConflicts": 5,
    "currentActiveLocks": 15,
    "successRate": 95.0,
    "lockKeyStats": {
      "distributed:lock:storage:PRODUCT001": 100,
      "distributed:lock:storage:PRODUCT002": 80
    },
    "serviceStats": {
      "seata-business": {
        "serviceName": "seata-business",
        "totalRequests": 600,
        "successfulRequests": 570,
        "failedRequests": 30,
        "successRate": 95.0,
        "averageWaitTime": 45,
        "averageHoldTime": 1800,
        "maxWaitTime": 400,
        "maxHoldTime": 25000
      },
      "seata-storage": {
        "serviceName": "seata-storage",
        "totalRequests": 400,
        "successfulRequests": 380,
        "failedRequests": 20,
        "successRate": 95.0,
        "averageWaitTime": 60,
        "averageHoldTime": 2300,
        "maxWaitTime": 500,
        "maxHoldTime": 30000
      }
    }
  },
  "timestamp": "2024-01-01T10:30:05"
}
```

### 4. 強制釋放鎖

**接口地址：** `DELETE /api/lock-management/locks/{lockKey}`

**描述：** 強制釋放指定的分布式鎖

**路徑參數：**
- `lockKey`: 鎖鍵

**響應示例：**
```json
{
  "success": true,
  "message": "成功強制釋放鎖: distributed:lock:storage:PRODUCT001",
  "data": true,
  "timestamp": "2024-01-01T10:30:05"
}
```

### 5. 批量強制釋放鎖

**接口地址：** `DELETE /api/lock-management/locks/batch`

**描述：** 批量強制釋放多個分布式鎖

**請求體：**
```json
[
  "distributed:lock:storage:PRODUCT001",
  "distributed:lock:storage:PRODUCT002",
  "distributed:lock:storage:PRODUCT003"
]
```

**響應示例：**
```json
{
  "success": true,
  "message": "批量強制釋放完成，成功釋放 2/3 個鎖",
  "data": [
    "distributed:lock:storage:PRODUCT001",
    "distributed:lock:storage:PRODUCT003"
  ],
  "timestamp": "2024-01-01T10:30:05"
}
```

### 6. 檢測跨服務鎖衝突

**接口地址：** `GET /api/lock-management/conflicts`

**描述：** 檢測當前存在的跨服務鎖衝突

**響應示例：**
```json
{
  "success": true,
  "message": "檢測到 1 個跨服務鎖衝突",
  "data": {
    "distributed:lock:storage:PRODUCT001": {
      "lockKey": "distributed:lock:storage:PRODUCT001",
      "currentHolder": "seata-business-instance-1",
      "currentHolderService": "seata-business",
      "waitingServices": ["seata-storage"],
      "conflictStartTime": "2024-01-01T10:25:00",
      "conflictCount": 3,
      "conflictDuration": 300000
    }
  },
  "timestamp": "2024-01-01T10:30:05"
}
```

### 7. 獲取服務鎖使用情況

**接口地址：** `GET /api/lock-management/service-usage`

**描述：** 獲取各服務的鎖使用統計情況

**響應示例：**
```json
{
  "success": true,
  "message": "成功獲取服務鎖使用情況",
  "data": {
    "seata-business": {
      "serviceName": "seata-business",
      "activeLocks": 8,
      "totalLockRequests": 600,
      "successfulLocks": 570,
      "successRate": 95.0,
      "averageHoldTime": 1800,
      "maxHoldTime": 25000,
      "totalConflicts": 3
    },
    "seata-storage": {
      "serviceName": "seata-storage",
      "activeLocks": 7,
      "totalLockRequests": 400,
      "successfulLocks": 380,
      "successRate": 95.0,
      "averageHoldTime": 2300,
      "maxHoldTime": 30000,
      "totalConflicts": 2
    }
  },
  "timestamp": "2024-01-01T10:30:05"
}
```

### 8. 獲取活躍鎖數量

**接口地址：** `GET /api/lock-management/active-count`

**描述：** 獲取當前活躍的鎖數量

**響應示例：**
```json
{
  "success": true,
  "message": "成功獲取活躍鎖數量",
  "data": 15,
  "timestamp": "2024-01-01T10:30:05"
}
```

### 9. 檢測死鎖風險

**接口地址：** `GET /api/lock-management/deadlock-risk`

**描述：** 檢測系統中潛在的死鎖風險

**響應示例：**
```json
{
  "success": true,
  "message": "檢測到 1 個潛在死鎖風險",
  "data": [
    {
      "lockKey": "distributed:lock:storage:PRODUCT001",
      "holderService": "seata-business",
      "waitingServices": ["seata-storage"],
      "riskScore": 75,
      "riskDescription": "長時間持有鎖且有多個服務等待",
      "riskLevel": "HIGH"
    }
  ],
  "timestamp": "2024-01-01T10:30:05"
}
```

### 10. 獲取長時間持有的鎖

**接口地址：** `GET /api/lock-management/long-held-locks`

**描述：** 獲取持有時間超過閾值的鎖信息

**請求參數：**
- `thresholdSeconds` (可選): 閾值時間（秒），默認 300 秒

**響應示例：**
```json
{
  "success": true,
  "message": "找到 2 個持有時間超過 300 秒的鎖",
  "data": [
    {
      "lockKey": "distributed:lock:storage:PRODUCT001",
      "holder": "seata-business-instance-1",
      "serviceSource": "seata-business",
      "acquireTime": "2024-01-01T10:20:00",
      "leaseTime": 30,
      "remainingTime": 25,
      "businessContext": "business-direct-deduct",
      "status": "ACTIVE",
      "lockType": "STORAGE_DEDUCT",
      "holdDuration": 600,
      "expired": false
    }
  ],
  "timestamp": "2024-01-01T10:30:05"
}
```

### 11. 重置統計信息

**接口地址：** `POST /api/lock-management/statistics/reset`

**描述：** 重置所有鎖統計信息

**響應示例：**
```json
{
  "success": true,
  "message": "成功重置鎖統計信息",
  "data": "統計信息已重置",
  "timestamp": "2024-01-01T10:30:05"
}
```

### 12. 健康檢查

**接口地址：** `GET /api/lock-management/health`

**描述：** 檢查分布式鎖服務的健康狀態

**響應示例：**
```json
{
  "success": true,
  "message": "分布式鎖服務運行正常",
  "data": {
    "status": "UP",
    "activeLocks": 15,
    "totalRequests": 1000,
    "successRate": 95.0,
    "crossServiceConflicts": 5
  },
  "timestamp": "2024-01-01T10:30:05"
}
```

## 錯誤響應格式

當 API 調用失敗時，會返回以下格式的錯誤響應：

```json
{
  "success": false,
  "message": "錯誤描述信息",
  "data": null,
  "timestamp": "2024-01-01T10:30:05",
  "errorCode": "LOCK_001"
}
```

## 使用示例

### 使用 curl 調用 API

```bash
# 獲取所有鎖狀態
curl -X GET "http://localhost:11000/api/lock-management/locks"

# 獲取指定服務的鎖狀態
curl -X GET "http://localhost:11000/api/lock-management/locks?serviceSource=seata-business"

# 獲取統計信息
curl -X GET "http://localhost:11000/api/lock-management/statistics"

# 強制釋放鎖
curl -X DELETE "http://localhost:11000/api/lock-management/locks/distributed:lock:storage:PRODUCT001"

# 批量強制釋放鎖
curl -X DELETE "http://localhost:11000/api/lock-management/locks/batch" \
     -H "Content-Type: application/json" \
     -d '["lock1", "lock2", "lock3"]'

# 檢測衝突
curl -X GET "http://localhost:11000/api/lock-management/conflicts"

# 健康檢查
curl -X GET "http://localhost:11000/api/lock-management/health"
```

## 注意事項

1. **權限控制**：強制釋放鎖的操作需要謹慎使用，建議在生產環境中添加適當的權限控制。

2. **監控告警**：建議對長時間持有的鎖和頻繁的跨服務衝突設置監控告警。

3. **性能考慮**：統計信息的查詢可能會對 Redis 性能產生影響，建議在低峰期進行。

4. **日誌記錄**：所有管理操作都會記錄詳細的日誌，便於問題排查。

5. **版本兼容性**：API 接口遵循 RESTful 設計原則，支持版本化管理。