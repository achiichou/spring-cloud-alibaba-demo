package com.atguigu.storage.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式鎖註解
 * 用於標記需要分布式鎖保護的方法，支持SpEL表達式動態生成鎖鍵
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLockable {
    
    /**
     * 鎖的鍵表達式，支持SpEL表達式
     * 例如：'storage:' + #commodityCode
     * 例如：'batch:' + T(java.util.Arrays).toString(#commodityCodes)
     */
    String key();
    
    /**
     * 等待時間（秒），默認5秒
     * 超過此時間仍無法獲取鎖則根據失敗策略處理
     */
    long waitTime() default 5;
    
    /**
     * 鎖持有時間（秒），默認30秒
     * 超過此時間鎖會自動釋放，防止死鎖
     */
    long leaseTime() default 30;
    
    /**
     * 獲取鎖失敗時的處理策略
     * 默認拋出異常
     */
    LockFailStrategy failStrategy() default LockFailStrategy.EXCEPTION;
    
    /**
     * 業務上下文描述
     * 用於日誌記錄和監控，幫助識別鎖的業務用途
     */
    String businessContext() default "";
    
    /**
     * 是否啟用跨服務鎖
     * 默認true，表示這是一個跨服務的分布式鎖
     */
    boolean crossService() default true;
    
    /**
     * 鎖的優先級
     * 當多個服務競爭同一個鎖時，優先級高的服務優先獲取
     * 數值越小優先級越高，默認為0（最高優先級）
     */
    int priority() default 0;
}