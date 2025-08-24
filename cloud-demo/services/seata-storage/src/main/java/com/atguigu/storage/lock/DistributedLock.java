package com.atguigu.storage.lock;

/**
 * 分布式鎖接口
 * 提供跨服務的分布式鎖功能，支持基於Redis的鎖實現
 */
public interface DistributedLock {
    
    /**
     * 嘗試獲取鎖
     * @param lockKey 鎖的鍵
     * @param waitTime 等待時間（秒）
     * @param leaseTime 鎖持有時間（秒）
     * @return 是否成功獲取鎖
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime);
    
    /**
     * 嘗試獲取鎖（使用默認參數）
     * @param lockKey 鎖的鍵
     * @return 是否成功獲取鎖
     */
    boolean tryLock(String lockKey);
    
    /**
     * 釋放鎖
     * @param lockKey 鎖的鍵
     */
    void unlock(String lockKey);
    
    /**
     * 檢查鎖是否存在
     * @param lockKey 鎖的鍵
     * @return 鎖是否存在
     */
    boolean isLocked(String lockKey);
    
    /**
     * 檢查當前線程是否持有指定鎖
     * @param lockKey 鎖的鍵
     * @return 是否持有鎖
     */
    boolean isHeldByCurrentThread(String lockKey);
    
    /**
     * 獲取鎖的剩餘時間
     * @param lockKey 鎖的鍵
     * @return 剩餘時間（毫秒），-1表示永不過期，-2表示鎖不存在
     */
    long getRemainingTime(String lockKey);
}