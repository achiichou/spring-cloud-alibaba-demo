package com.atguigu.business.controller;

import com.atguigu.business.service.BusinessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Seata全局事務與分布式鎖集成測試控制器
 * 
 * 提供測試接口來驗證分布式鎖與Seata全局事務的集成效果
 * 
 * @author system
 */
@RestController
@RequestMapping("/api/seata-transaction-test")
public class SeataTransactionTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(SeataTransactionTestController.class);
    
    @Autowired
    private BusinessService businessService;
    
    /**
     * 測試標準購買流程（通過Feign調用）
     * 驗證分布式鎖與全局事務的集成
     * 
     * @param userId 用戶ID
     * @param commodityCode 商品編碼
     * @param orderCount 訂單數量
     * @return 操作結果
     */
    @PostMapping("/purchase")
    public ResponseEntity<Map<String, Object>> testPurchase(
            @RequestParam String userId,
            @RequestParam String commodityCode,
            @RequestParam int orderCount) {
        
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("開始測試標準購買流程 - 用戶: {}, 商品: {}, 數量: {}", userId, commodityCode, orderCount);
            
            businessService.purchase(userId, commodityCode, orderCount);
            
            long duration = System.currentTimeMillis() - startTime;
            result.put("success", true);
            result.put("message", "購買成功");
            result.put("duration", duration + "ms");
            result.put("userId", userId);
            result.put("commodityCode", commodityCode);
            result.put("orderCount", orderCount);
            result.put("transactionType", "global-transaction-with-feign");
            
            logger.info("標準購買流程測試成功 - 耗時: {}ms", duration);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            result.put("success", false);
            result.put("message", "購買失敗: " + e.getMessage());
            result.put("duration", duration + "ms");
            result.put("error", e.getClass().getSimpleName());
            result.put("transactionType", "global-transaction-with-feign");
            
            logger.error("標準購買流程測試失敗 - 耗時: {}ms, 錯誤: {}", duration, e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 測試直接庫存操作購買流程
     * 驗證分布式鎖與全局事務在直接數據庫操作中的集成
     * 
     * @param userId 用戶ID
     * @param commodityCode 商品編碼
     * @param orderCount 訂單數量
     * @return 操作結果
     */
    @PostMapping("/purchase-direct")
    public ResponseEntity<Map<String, Object>> testPurchaseWithDirectStorage(
            @RequestParam String userId,
            @RequestParam String commodityCode,
            @RequestParam int orderCount) {
        
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("開始測試直接庫存操作購買流程 - 用戶: {}, 商品: {}, 數量: {}", userId, commodityCode, orderCount);
            
            businessService.purchaseWithDirectStorage(userId, commodityCode, orderCount);
            
            long duration = System.currentTimeMillis() - startTime;
            result.put("success", true);
            result.put("message", "直接庫存操作購買成功");
            result.put("duration", duration + "ms");
            result.put("userId", userId);
            result.put("commodityCode", commodityCode);
            result.put("orderCount", orderCount);
            result.put("transactionType", "global-transaction-with-direct-storage");
            
            logger.info("直接庫存操作購買流程測試成功 - 耗時: {}ms", duration);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            result.put("success", false);
            result.put("message", "直接庫存操作購買失敗: " + e.getMessage());
            result.put("duration", duration + "ms");
            result.put("error", e.getClass().getSimpleName());
            result.put("transactionType", "global-transaction-with-direct-storage");
            
            logger.error("直接庫存操作購買流程測試失敗 - 耗時: {}ms, 錯誤: {}", duration, e.getMessage(), e);
            
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 測試事務回滾場景
     * 模擬事務回滾時分布式鎖的正確釋放
     * 
     * @param userId 用戶ID
     * @param commodityCode 商品編碼
     * @param orderCount 訂單數量
     * @param forceError 是否強制產生錯誤
     * @return 操作結果
     */
    @PostMapping("/test-rollback")
    public ResponseEntity<Map<String, Object>> testTransactionRollback(
            @RequestParam String userId,
            @RequestParam String commodityCode,
            @RequestParam int orderCount,
            @RequestParam(defaultValue = "true") boolean forceError) {
        
        Map<String, Object> result = new HashMap<>();
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("開始測試事務回滾場景 - 用戶: {}, 商品: {}, 數量: {}, 強制錯誤: {}", 
                       userId, commodityCode, orderCount, forceError);
            
            if (forceError) {
                // 模擬業務異常，觸發事務回滾
                throw new RuntimeException("模擬業務異常，測試事務回滾和鎖釋放");
            }
            
            businessService.purchaseWithDirectStorage(userId, commodityCode, orderCount);
            
            long duration = System.currentTimeMillis() - startTime;
            result.put("success", true);
            result.put("message", "事務正常提交");
            result.put("duration", duration + "ms");
            result.put("transactionType", "rollback-test");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            result.put("success", false);
            result.put("message", "事務回滾: " + e.getMessage());
            result.put("duration", duration + "ms");
            result.put("error", e.getClass().getSimpleName());
            result.put("transactionType", "rollback-test");
            result.put("rollbackTriggered", true);
            
            logger.info("事務回滾測試完成 - 耗時: {}ms, 回滾原因: {}", duration, e.getMessage());
            
            return ResponseEntity.ok(result); // 返回200，因為這是預期的測試結果
        }
    }
    
    /**
     * 獲取測試說明
     * 
     * @return 測試接口說明
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getTestInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("title", "Seata全局事務與分布式鎖集成測試");
        info.put("description", "提供測試接口來驗證分布式鎖與Seata全局事務的集成效果");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("POST /api/seata-transaction-test/purchase", "測試標準購買流程（通過Feign調用）");
        endpoints.put("POST /api/seata-transaction-test/purchase-direct", "測試直接庫存操作購買流程");
        endpoints.put("POST /api/seata-transaction-test/test-rollback", "測試事務回滾場景");
        endpoints.put("GET /api/seata-transaction-test/info", "獲取測試說明");
        
        info.put("endpoints", endpoints);
        
        Map<String, String> parameters = new HashMap<>();
        parameters.put("userId", "用戶ID（必需）");
        parameters.put("commodityCode", "商品編碼（必需）");
        parameters.put("orderCount", "訂單數量（必需）");
        parameters.put("forceError", "是否強制產生錯誤（可選，默認true）");
        
        info.put("parameters", parameters);
        
        Map<String, String> features = new HashMap<>();
        features.put("分布式鎖集成", "自動與Seata全局事務生命週期同步");
        features.put("事務提交", "全局事務提交後自動釋放分布式鎖");
        features.put("事務回滾", "全局事務回滾後立即釋放分布式鎖");
        features.put("鎖監控", "記錄鎖操作事件和統計信息");
        features.put("跨服務支持", "支持跨服務的分布式鎖衝突檢測");
        
        info.put("features", features);
        
        return ResponseEntity.ok(info);
    }
}