package com.atguigu.business.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CrossServiceLockKeyGenerator 單元測試
 */
class CrossServiceLockKeyGeneratorTest {
    
    private CrossServiceLockKeyGenerator keyGenerator;
    
    @BeforeEach
    void setUp() {
        keyGenerator = new CrossServiceLockKeyGenerator();
    }
    
    @Test
    void testGenerateStorageLockKey() {
        // 測試正常情況
        String lockKey = keyGenerator.generateStorageLockKey("PRODUCT001");
        assertEquals("distributed:lock:storage:product001", lockKey);
        
        // 測試大小寫轉換和空格處理
        String lockKey2 = keyGenerator.generateStorageLockKey("  PRODUCT002  ");
        assertEquals("distributed:lock:storage:product002", lockKey2);
    }
    
    @Test
    void testGenerateStorageLockKeyWithEmptyInput() {
        // 測試空字符串
        assertThrows(IllegalArgumentException.class, () -> {
            keyGenerator.generateStorageLockKey("");
        });
        
        // 測試null
        assertThrows(IllegalArgumentException.class, () -> {
            keyGenerator.generateStorageLockKey(null);
        });
        
        // 測試只有空格
        assertThrows(IllegalArgumentException.class, () -> {
            keyGenerator.generateStorageLockKey("   ");
        });
    }
    
    @Test
    void testGenerateBatchLockKey() {
        List<String> codes = Arrays.asList("PRODUCT001", "PRODUCT002", "PRODUCT003");
        String batchKey = keyGenerator.generateBatchLockKey(codes);
        
        // 驗證批量鎖鍵格式
        assertTrue(batchKey.startsWith("distributed:lock:storage:batch:"));
        assertTrue(batchKey.length() > "distributed:lock:storage:batch:".length());
        
        // 測試順序無關性 - 相同的商品組合應該生成相同的鎖鍵
        List<String> codes2 = Arrays.asList("PRODUCT003", "PRODUCT001", "PRODUCT002");
        String batchKey2 = keyGenerator.generateBatchLockKey(codes2);
        assertEquals(batchKey, batchKey2);
    }
    
    @Test
    void testGenerateBatchLockKeyWithDuplicates() {
        // 測試去重功能
        List<String> codesWithDuplicates = Arrays.asList("PRODUCT001", "PRODUCT002", "PRODUCT001", "PRODUCT003");
        List<String> codesWithoutDuplicates = Arrays.asList("PRODUCT001", "PRODUCT002", "PRODUCT003");
        
        String batchKey1 = keyGenerator.generateBatchLockKey(codesWithDuplicates);
        String batchKey2 = keyGenerator.generateBatchLockKey(codesWithoutDuplicates);
        
        assertEquals(batchKey1, batchKey2);
    }
    
    @Test
    void testGenerateBatchLockKeyWithEmptyInput() {
        // 測試空列表
        assertThrows(IllegalArgumentException.class, () -> {
            keyGenerator.generateBatchLockKey(Arrays.asList());
        });
        
        // 測試null列表
        assertThrows(IllegalArgumentException.class, () -> {
            keyGenerator.generateBatchLockKey(null);
        });
        
        // 測試包含空元素的列表
        assertThrows(IllegalArgumentException.class, () -> {
            keyGenerator.generateBatchLockKey(Arrays.asList("PRODUCT001", "", "PRODUCT002"));
        });
    }
    
    @Test
    void testGenerateMultipleStorageLockKeys() {
        List<String> codes = Arrays.asList("PRODUCT001", "PRODUCT002", "PRODUCT001");
        List<String> lockKeys = keyGenerator.generateMultipleStorageLockKeys(codes);
        
        // 驗證去重和排序
        assertEquals(2, lockKeys.size());
        assertEquals("distributed:lock:storage:product001", lockKeys.get(0));
        assertEquals("distributed:lock:storage:product002", lockKeys.get(1));
        
        // 驗證排序（字典序）
        assertTrue(lockKeys.get(0).compareTo(lockKeys.get(1)) < 0);
    }
    
    @Test
    void testIsValidStorageLockKey() {
        // 測試有效的鎖鍵
        assertTrue(keyGenerator.isValidStorageLockKey("distributed:lock:storage:product001"));
        assertTrue(keyGenerator.isValidStorageLockKey("distributed:lock:storage:batch:abc123"));
        
        // 測試無效的鎖鍵
        assertFalse(keyGenerator.isValidStorageLockKey("invalid:key"));
        assertFalse(keyGenerator.isValidStorageLockKey(""));
        assertFalse(keyGenerator.isValidStorageLockKey(null));
    }
    
    @Test
    void testExtractCommodityCode() {
        // 測試正常提取
        String commodityCode = keyGenerator.extractCommodityCode("distributed:lock:storage:product001");
        assertEquals("product001", commodityCode);
        
        // 測試批量鎖鍵（應該返回null）
        String batchResult = keyGenerator.extractCommodityCode("distributed:lock:storage:batch:abc123");
        assertNull(batchResult);
        
        // 測試無效鎖鍵
        String invalidResult = keyGenerator.extractCommodityCode("invalid:key");
        assertNull(invalidResult);
    }
    
    @Test
    void testIsBatchLockKey() {
        // 測試批量鎖鍵
        assertTrue(keyGenerator.isBatchLockKey("distributed:lock:storage:batch:abc123"));
        
        // 測試單個鎖鍵
        assertFalse(keyGenerator.isBatchLockKey("distributed:lock:storage:product001"));
        
        // 測試無效鎖鍵
        assertFalse(keyGenerator.isBatchLockKey("invalid:key"));
        assertFalse(keyGenerator.isBatchLockKey(""));
        assertFalse(keyGenerator.isBatchLockKey(null));
    }
    
    @Test
    void testCrossServiceConsistency() {
        // 測試跨服務一致性 - 相同輸入應該產生相同輸出
        String code = "PRODUCT001";
        String lockKey1 = keyGenerator.generateStorageLockKey(code);
        String lockKey2 = keyGenerator.generateStorageLockKey(code);
        
        assertEquals(lockKey1, lockKey2);
        
        // 測試批量操作的一致性
        List<String> codes = Arrays.asList("PRODUCT001", "PRODUCT002");
        String batchKey1 = keyGenerator.generateBatchLockKey(codes);
        String batchKey2 = keyGenerator.generateBatchLockKey(codes);
        
        assertEquals(batchKey1, batchKey2);
    }
}