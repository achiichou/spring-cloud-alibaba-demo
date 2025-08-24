package com.atguigu.storage.lock;

/**
 * 鎖獲取失敗處理策略枚舉
 * 定義當分布式鎖獲取失敗時的不同處理方式
 */
public enum LockFailStrategy {
    
    /**
     * 拋出異常策略
     * 當獲取鎖失敗時，拋出DistributedLockException異常
     * 適用於必須獲取鎖才能執行的關鍵業務邏輯
     */
    EXCEPTION("拋出異常", "當獲取鎖失敗時拋出異常"),
    
    /**
     * 返回null策略
     * 當獲取鎖失敗時，方法返回null
     * 適用於可選的業務邏輯，調用方需要檢查返回值
     */
    RETURN_NULL("返回null", "當獲取鎖失敗時返回null"),
    
    /**
     * 重試策略
     * 當獲取鎖失敗時，使用指數退避算法進行重試
     * 適用於對時效性要求不高但必須執行的業務邏輯
     */
    RETRY("重試", "當獲取鎖失敗時進行重試"),
    
    /**
     * 降級處理策略
     * 當獲取鎖失敗時，執行降級邏輯（如使用本地鎖或跳過鎖保護）
     * 適用於高可用性要求的場景，但可能犧牲一致性
     */
    FALLBACK("降級處理", "當獲取鎖失敗時執行降級邏輯"),
    
    /**
     * 快速失敗策略
     * 當獲取鎖失敗時，立即返回失敗結果，不等待
     * 適用於對響應時間要求極高的場景
     */
    FAST_FAIL("快速失敗", "當獲取鎖失敗時立即返回失敗"),
    
    /**
     * 忽略策略
     * 當獲取鎖失敗時，忽略鎖保護直接執行業務邏輯
     * 適用於對一致性要求不高的場景，慎用
     */
    IGNORE("忽略", "當獲取鎖失敗時忽略鎖保護直接執行");
    
    private final String description;
    private final String detail;
    
    LockFailStrategy(String description, String detail) {
        this.description = description;
        this.detail = detail;
    }
    
    /**
     * 獲取策略描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 獲取策略詳細說明
     */
    public String getDetail() {
        return detail;
    }
    
    /**
     * 判斷是否需要拋出異常
     */
    public boolean shouldThrowException() {
        return this == EXCEPTION;
    }
    
    /**
     * 判斷是否需要重試
     */
    public boolean shouldRetry() {
        return this == RETRY;
    }
    
    /**
     * 判斷是否需要降級處理
     */
    public boolean shouldFallback() {
        return this == FALLBACK;
    }
    
    /**
     * 判斷是否應該快速失敗
     */
    public boolean shouldFastFail() {
        return this == FAST_FAIL;
    }
    
    /**
     * 判斷是否應該忽略鎖保護
     */
    public boolean shouldIgnore() {
        return this == IGNORE;
    }
    
    @Override
    public String toString() {
        return String.format("%s: %s", description, detail);
    }
}