package com.atguigu.business.lock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CrossServiceLockKeyGenerator 集成測試
 * 驗證跨服務鎖鍵生成的一致性和正確性
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/storage_db?useUnicode=true&characterEncoding=utf-8&useSSL=false",
    "spring.datasource.username=root",
    "spring.datasource.password=123456",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.redis.host=localhost",
    "spring.redis.port=6379"
})
class CrossServiceLockKeyGeneratorIntegrationTest {
    
    @Test
    void testCrossServiceKeyConsistency() {
        // 模擬兩個服務使用相同的鎖鍵生成器
        CrossServiceLockKeyGenerator businessKeyGenerator = new CrossServiceLockKeyGenerator();
        CrossServiceLockKeyGenerator storageKeyGenerator = new CrossServiceLockKeyGenerator();
        
        // 測試單個商品鎖鍵的一致性
        String commodityCode = "PRODUCT001";
        String businessLockKey = businessKeyGenerator.generateStorageLockKey(commodityCode);
        String storageLockKey = storageKeyGenerator.generateStorageLockKey(commodityCode);
        
        assertEquals(businessLockKey, storageLockKey, 
            "兩個服務對同一商品應該生成相同的鎖鍵");
        
        // 測試批量操作鎖鍵的一致性
        List<String> commodityCodes = Arrays.asList("PRODUCT001", "PRODUCT002", "PRODUCT003");
        String businessBatchKey = businessKeyGenerator.generateBatchLockKey(commodityCodes);
        String storageBatchKey = storageKeyGenerator.generateBatchLockKey(commodityCodes);
        
        assertEquals(businessBatchKey, storageBatchKey, 
            "兩個服務對同一批商品應該生成相同的批量鎖鍵");
    }
    
    @Test
    void testLockKeyFormat() {
        CrossServiceLockKeyGenerator keyGenerator = new CrossServiceLockKeyGenerator();
        
        // 測試鎖鍵格式符合設計規範
        String lockKey = keyGenerator.generateStorageLockKey("PRODUCT001");
        assertEquals("distributed:lock:storage:product001", lockKey);
        
        // 測試批量鎖鍵格式
        List<String> codes = Arrays.asList("PRODUCT001", "PRODUCT002");
        String batchKey = keyGenerator.generateBatchLockKey(codes);
        assertTrue(batchKey.startsWith("distributed:lock:storage:batch:"));
        
        // 驗證鎖鍵有效性
        assertTrue(keyGenerator.isValidStorageLockKey(lockKey));
        assertTrue(keyGenerator.isValidStorageLockKey(batchKey));
        assertTrue(keyGenerator.isBatchLockKey(batchKey));
        assertFalse(keyGenerator.isBatchLockKey(lockKey));
    }
    
    @Test
    void testDeadlockPrevention() {
        CrossServiceLockKeyGenerator keyGenerator = new CrossServiceLockKeyGenerator();
        
        // 測試多個鎖鍵的排序，防止死鎖
        List<String> codes1 = Arrays.asList("PRODUCT003", "PRODUCT001", "PRODUCT002");
        List<String> codes2 = Arrays.asList("PRODUCT001", "PRODUCT003", "PRODUCT002");
        
        List<String> lockKeys1 = keyGenerator.generateMultipleStorageLockKeys(codes1);
        List<String> lockKeys2 = keyGenerator.generateMultipleStorageLockKeys(codes2);
        
        // 無論輸入順序如何，輸出的鎖鍵順序應該相同
        assertEquals(lockKeys1, lockKeys2);
        
        // 驗證鎖鍵是按字典序排序的
        for (int i = 0; i < lockKeys1.size() - 1; i++) {
            assertTrue(lockKeys1.get(i).compareTo(lockKeys1.get(i + 1)) < 0);
        }
    }
    
    @Test
    void testBusinessScenarios() {
        CrossServiceLockKeyGenerator keyGenerator = new CrossServiceLockKeyGenerator();
        
        // 場景1：seata-business服務直接扣減庫存
        String businessLockKey = keyGenerator.generateStorageLockKey("PHONE001");
        assertEquals("distributed:lock:storage:phone001", businessLockKey);
        
        // 場景2：seata-storage服務扣減庫存
        String storageLockKey = keyGenerator.generateStorageLockKey("PHONE001");
        assertEquals("distributed:lock:storage:phone001", storageLockKey);
        
        // 驗證兩個服務會競爭同一個鎖
        assertEquals(businessLockKey, storageLockKey);
        
        // 場景3：批量操作
        List<String> batchCodes = Arrays.asList("PHONE001", "LAPTOP001", "TABLET001");
        String batchLockKey = keyGenerator.generateBatchLockKey(batchCodes);
        assertTrue(batchLockKey.startsWith("distributed:lock:storage:batch:"));
        
        // 場景4：提取商品編碼
        String extractedCode = keyGenerator.extractCommodityCode(businessLockKey);
        assertEquals("phone001", extractedCode);
        
        // 批量鎖鍵無法提取單個商品編碼
        String batchExtracted = keyGenerator.extractCommodityCode(batchLockKey);
        assertNull(batchExtracted);
    }
}