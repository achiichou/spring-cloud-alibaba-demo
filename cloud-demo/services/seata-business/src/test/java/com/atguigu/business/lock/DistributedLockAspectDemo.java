package com.atguigu.business.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 分布式鎖AOP切面演示類
 * 
 * 展示如何使用@DistributedLockable註解實現跨服務的分布式鎖功能。
 * 這個類包含了各種使用場景的示例方法。
 */
@Component
public class DistributedLockAspectDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(DistributedLockAspectDemo.class);
    
    /**
     * 示例1：基本的庫存扣減操作
     * 使用SpEL表達式生成基於商品編碼的鎖鍵
     */
    @DistributedLockable(
        key = "'storage:' + #commodityCode",
        waitTime = 5,
        leaseTime = 30,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "demo-storage-deduct"
    )
    public String demonstrateBasicStorageDeduct(String commodityCode, int count) {
        logger.info("執行庫存扣減操作 - 商品編碼: {}, 數量: {}", commodityCode, count);
        
        // 模擬業務邏輯處理時間
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return String.format("成功扣減庫存 - 商品: %s, 數量: %d", commodityCode, count);
    }
    
    /**
     * 示例2：批量操作
     * 使用複雜的SpEL表達式處理數組參數
     */
    @DistributedLockable(
        key = "'batch:' + T(java.util.Arrays).toString(#commodityCodes)",
        waitTime = 10,
        leaseTime = 60,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "demo-batch-operation"
    )
    public String demonstrateBatchOperation(String[] commodityCodes, int[] counts) {
        logger.info("執行批量操作 - 商品數量: {}", commodityCodes.length);
        
        // 模擬批量處理邏輯
        for (int i = 0; i < commodityCodes.length; i++) {
            logger.info("處理商品 {}: {}, 數量: {}", i + 1, commodityCodes[i], counts[i]);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return String.format("批量操作完成 - 處理了 %d 個商品", commodityCodes.length);
    }
    
    /**
     * 示例3：使用RETURN_NULL失敗策略
     * 當獲取鎖失敗時返回null而不是拋出異常
     */
    @DistributedLockable(
        key = "'optional:' + #resourceId",
        waitTime = 2,
        leaseTime = 15,
        failStrategy = LockFailStrategy.RETURN_NULL,
        businessContext = "demo-optional-operation"
    )
    public String demonstrateOptionalOperation(String resourceId) {
        logger.info("執行可選操作 - 資源ID: {}", resourceId);
        
        // 模擬可選的業務邏輯
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return String.format("可選操作完成 - 資源: %s", resourceId);
    }
    
    /**
     * 示例4：使用RETRY失敗策略
     * 當獲取鎖失敗時自動重試
     */
    @DistributedLockable(
        key = "'retry:' + #taskId",
        waitTime = 3,
        leaseTime = 20,
        failStrategy = LockFailStrategy.RETRY,
        businessContext = "demo-retry-operation"
    )
    public String demonstrateRetryOperation(String taskId) {
        logger.info("執行重試操作 - 任務ID: {}", taskId);
        
        // 模擬需要重試的業務邏輯
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return String.format("重試操作完成 - 任務: %s", taskId);
    }
    
    /**
     * 示例5：使用FALLBACK失敗策略
     * 當獲取鎖失敗時執行降級邏輯
     */
    @DistributedLockable(
        key = "'fallback:' + #operationId",
        waitTime = 1,
        leaseTime = 10,
        failStrategy = LockFailStrategy.FALLBACK,
        businessContext = "demo-fallback-operation"
    )
    public String demonstrateFallbackOperation(String operationId) {
        logger.info("執行降級操作 - 操作ID: {}", operationId);
        
        // 模擬高可用性要求的業務邏輯
        try {
            Thread.sleep(600);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return String.format("降級操作完成 - 操作: %s", operationId);
    }
    
    /**
     * 示例6：使用IGNORE失敗策略
     * 當獲取鎖失敗時忽略鎖保護直接執行
     */
    @DistributedLockable(
        key = "'ignore:' + #dataId",
        waitTime = 1,
        leaseTime = 5,
        failStrategy = LockFailStrategy.IGNORE,
        businessContext = "demo-ignore-operation"
    )
    public String demonstrateIgnoreOperation(String dataId) {
        logger.info("執行忽略鎖保護操作 - 數據ID: {}", dataId);
        
        // 模擬對一致性要求不高的業務邏輯
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return String.format("忽略鎖保護操作完成 - 數據: %s", dataId);
    }
    
    /**
     * 示例7：複雜的SpEL表達式
     * 展示如何使用複雜的SpEL表達式生成動態鎖鍵
     */
    @DistributedLockable(
        key = "'complex:' + #user.id + ':' + #operation.type + ':' + T(java.time.LocalDate).now().toString()",
        waitTime = 5,
        leaseTime = 25,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "demo-complex-spel"
    )
    public String demonstrateComplexSpelExpression(User user, Operation operation) {
        logger.info("執行複雜SpEL表達式操作 - 用戶: {}, 操作類型: {}", user.getId(), operation.getType());
        
        // 模擬複雜的業務邏輯
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return String.format("複雜操作完成 - 用戶: %s, 操作: %s", user.getId(), operation.getType());
    }
    
    /**
     * 示例8：跨服務鎖衝突演示
     * 使用與seata-storage服務相同的鎖鍵格式
     */
    @DistributedLockable(
        key = "'storage:' + #commodityCode",
        waitTime = 8,
        leaseTime = 40,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "demo-cross-service-conflict"
    )
    public String demonstrateCrossServiceConflict(String commodityCode) {
        logger.info("演示跨服務鎖衝突 - 商品編碼: {}", commodityCode);
        
        // 這個方法使用與seata-storage服務相同的鎖鍵格式
        // 當兩個服務同時操作同一商品時，會發生跨服務鎖衝突
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return String.format("跨服務操作完成 - 商品: %s", commodityCode);
    }
    
    // 輔助類用於演示複雜SpEL表達式
    public static class User {
        private String id;
        private String name;
        
        public User(String id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
    }
    
    public static class Operation {
        private String type;
        private String description;
        
        public Operation(String type, String description) {
            this.type = type;
            this.description = description;
        }
        
        public String getType() { return type; }
        public String getDescription() { return description; }
    }
}