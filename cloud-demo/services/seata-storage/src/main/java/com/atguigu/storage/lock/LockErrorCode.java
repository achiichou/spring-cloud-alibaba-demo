package com.atguigu.storage.lock;

/**
 * 分布式鎖錯誤碼枚舉
 * 定義分布式鎖操作中可能出現的各種錯誤類型
 */
public enum LockErrorCode {
    
    /**
     * 獲取鎖超時
     */
    LOCK_ACQUIRE_TIMEOUT("LOCK_001", "獲取鎖超時", "在指定時間內無法獲取到分布式鎖"),
    
    /**
     * 釋放鎖失敗
     */
    LOCK_RELEASE_FAILED("LOCK_002", "釋放鎖失敗", "嘗試釋放分布式鎖時發生錯誤"),
    
    /**
     * Redis連接錯誤
     */
    REDIS_CONNECTION_ERROR("LOCK_003", "Redis連接錯誤", "無法連接到Redis服務器或連接異常"),
    
    /**
     * 無效鎖鍵
     */
    INVALID_LOCK_KEY("LOCK_004", "無效鎖鍵", "提供的鎖鍵格式不正確或為空"),
    
    /**
     * 鎖未持有
     */
    LOCK_NOT_HELD("LOCK_005", "鎖未持有", "嘗試釋放一個未被當前線程持有的鎖"),
    
    /**
     * 鎖已過期
     */
    LOCK_EXPIRED("LOCK_006", "鎖已過期", "鎖的租約時間已到期，鎖已被自動釋放"),
    
    /**
     * 跨服務鎖衝突
     */
    CROSS_SERVICE_CONFLICT("LOCK_007", "跨服務鎖衝突", "不同服務之間發生鎖競爭衝突"),
    
    /**
     * 鎖重入失敗
     */
    LOCK_REENTRANT_FAILED("LOCK_008", "鎖重入失敗", "同一線程嘗試重入鎖時失敗"),
    
    /**
     * 鎖配置錯誤
     */
    LOCK_CONFIG_ERROR("LOCK_009", "鎖配置錯誤", "分布式鎖相關配置參數錯誤"),
    
    /**
     * SpEL表達式解析錯誤
     */
    SPEL_EXPRESSION_ERROR("LOCK_010", "SpEL表達式解析錯誤", "鎖鍵的SpEL表達式解析失敗"),
    
    /**
     * 鎖操作被中斷
     */
    LOCK_INTERRUPTED("LOCK_011", "鎖操作被中斷", "等待獲取鎖的過程中線程被中斷"),
    
    /**
     * 鎖服務不可用
     */
    LOCK_SERVICE_UNAVAILABLE("LOCK_012", "鎖服務不可用", "分布式鎖服務暫時不可用"),
    
    /**
     * 批量鎖操作失敗
     */
    BATCH_LOCK_FAILED("LOCK_013", "批量鎖操作失敗", "批量獲取或釋放鎖時部分操作失敗"),
    
    /**
     * 鎖監控錯誤
     */
    LOCK_MONITOR_ERROR("LOCK_014", "鎖監控錯誤", "鎖監控功能發生錯誤"),
    
    /**
     * 未知錯誤
     */
    UNKNOWN_ERROR("LOCK_999", "未知錯誤", "發生了未預期的錯誤");
    
    private final String code;
    private final String message;
    private final String description;
    
    LockErrorCode(String code, String message, String description) {
        this.code = code;
        this.message = message;
        this.description = description;
    }
    
    /**
     * 獲取錯誤碼
     */
    public String getCode() {
        return code;
    }
    
    /**
     * 獲取錯誤消息
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * 獲取錯誤描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 根據錯誤碼查找對應的枚舉
     */
    public static LockErrorCode fromCode(String code) {
        for (LockErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }
    
    /**
     * 判斷是否為可重試的錯誤
     */
    public boolean isRetryable() {
        return this == LOCK_ACQUIRE_TIMEOUT || 
               this == REDIS_CONNECTION_ERROR || 
               this == LOCK_SERVICE_UNAVAILABLE ||
               this == LOCK_INTERRUPTED;
    }
    
    /**
     * 判斷是否為嚴重錯誤
     */
    public boolean isCritical() {
        return this == REDIS_CONNECTION_ERROR || 
               this == LOCK_SERVICE_UNAVAILABLE ||
               this == LOCK_CONFIG_ERROR;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s: %s", code, message, description);
    }
}