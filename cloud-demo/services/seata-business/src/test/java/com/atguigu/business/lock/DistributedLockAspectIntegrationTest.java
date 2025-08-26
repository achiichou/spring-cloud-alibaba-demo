package com.atguigu.business.lock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 分布式鎖AOP切面集成測試
 * 
 * 測試AOP切面在實際Spring環境中的工作情況，
 * 驗證@DistributedLockable註解的攔截和處理邏輯。
 */
@SpringBootTest(classes = {
    DistributedLockAspectIntegrationTest.TestService.class,
    DistributedLockAspect.class
})
@TestPropertySource(properties = {
    "spring.application.name=seata-business-test",
    "distributed.lock.enable-conflict-detection=true",
    "distributed.lock.max-retry-attempts=2",
    "distributed.lock.retry-base-delay=50"
})
class DistributedLockAspectIntegrationTest {
    
    @MockBean
    private DistributedLock distributedLock;
    
    @MockBean
    private CrossServiceLockKeyGenerator lockKeyGenerator;
    
    @Autowired
    private TestService testService;
    
    /**
     * 測試服務類，用於驗證AOP切面功能
     */
    @Service
    static class TestService {
        
        @DistributedLockable(
            key = "'storage:' + #commodityCode",
            waitTime = 5,
            leaseTime = 30,
            failStrategy = LockFailStrategy.EXCEPTION,
            businessContext = "test-storage-operation"
        )
        public String testStorageOperation(String commodityCode, int count) {
            return "操作成功：" + commodityCode + "，數量：" + count;
        }
        
        @DistributedLockable(
            key = "'batch:' + T(java.util.Arrays).toString(#commodityCodes)",
            waitTime = 3,
            leaseTime = 20,
            failStrategy = LockFailStrategy.RETURN_NULL
        )
        public String testBatchOperation(String[] commodityCodes) {
            return "批量操作成功，商品數量：" + commodityCodes.length;
        }
        
        @DistributedLockable(
            key = "'simple:' + #id",
            failStrategy = LockFailStrategy.IGNORE
        )
        public String testIgnoreStrategy(String id) {
            return "忽略鎖保護：" + id;
        }
        
        @DistributedLockable(
            key = "'fallback:' + #id",
            failStrategy = LockFailStrategy.FALLBACK
        )
        public String testFallbackStrategy(String id) {
            return "降級處理：" + id;
        }
    }
    
    @Test
    void testSuccessfulLockAcquisition() {
        // 測試成功獲取鎖的場景
        String commodityCode = "PRODUCT001";
        String expectedLockKey = "distributed:lock:storage:product001";
        
        // 模擬鎖鍵生成和鎖獲取成功
        when(lockKeyGenerator.isValidStorageLockKey(anyString())).thenReturn(false);
        when(lockKeyGenerator.generateStorageLockKey(commodityCode)).thenReturn(expectedLockKey);
        when(distributedLock.tryLock(eq(expectedLockKey), eq(5L), eq(30L))).thenReturn(true);
        when(distributedLock.isLocked(expectedLockKey)).thenReturn(false);
        
        // 執行測試方法
        String result = testService.testStorageOperation(commodityCode, 10);
        
        // 驗證結果
        assertEquals("操作成功：PRODUCT001，數量：10", result);
        
        // 驗證鎖操作被正確調用
        verify(distributedLock).tryLock(expectedLockKey, 5L, 30L);
        verify(distributedLock).unlock(expectedLockKey);
    }
    
    @Test
    void testLockAcquisitionFailureWithException() {
        // 測試獲取鎖失敗並拋出異常的場景
        String commodityCode = "PRODUCT002";
        String expectedLockKey = "distributed:lock:storage:product002";
        
        // 模擬鎖獲取失敗
        when(lockKeyGenerator.isValidStorageLockKey(anyString())).thenReturn(false);
        when(lockKeyGenerator.generateStorageLockKey(commodityCode)).thenReturn(expectedLockKey);
        when(distributedLock.tryLock(eq(expectedLockKey), eq(5L), eq(30L))).thenReturn(false);
        when(distributedLock.isLocked(expectedLockKey)).thenReturn(true);
        
        // 驗證拋出異常
        assertThrows(DistributedLockException.class, () -> {
            testService.testStorageOperation(commodityCode, 5);
        });
        
        // 驗證鎖操作被調用但未釋放（因為未獲取到）
        verify(distributedLock).tryLock(expectedLockKey, 5L, 30L);
        verify(distributedLock, never()).unlock(expectedLockKey);
    }
    
    @Test
    void testLockAcquisitionFailureWithReturnNull() {
        // 測試獲取鎖失敗並返回null的場景
        String[] commodityCodes = {"PRODUCT001", "PRODUCT002"};
        
        // 模擬鎖獲取失敗
        when(lockKeyGenerator.isValidStorageLockKey(anyString())).thenReturn(false);
        when(distributedLock.tryLock(anyString(), eq(3L), eq(20L))).thenReturn(false);
        when(distributedLock.isLocked(anyString())).thenReturn(true);
        
        // 執行測試方法
        String result = testService.testBatchOperation(commodityCodes);
        
        // 驗證返回null
        assertNull(result);
        
        // 驗證鎖操作被調用
        verify(distributedLock).tryLock(anyString(), eq(3L), eq(20L));
    }
    
    @Test
    void testIgnoreStrategy() {
        // 測試忽略策略：即使獲取鎖失敗也執行方法
        String id = "TEST001";
        
        // 模擬鎖獲取失敗
        when(lockKeyGenerator.isValidStorageLockKey(anyString())).thenReturn(false);
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong())).thenReturn(false);
        when(distributedLock.isLocked(anyString())).thenReturn(true);
        
        // 執行測試方法
        String result = testService.testIgnoreStrategy(id);
        
        // 驗證方法正常執行
        assertEquals("忽略鎖保護：TEST001", result);
        
        // 驗證鎖操作被調用
        verify(distributedLock).tryLock(anyString(), anyLong(), anyLong());
    }
    
    @Test
    void testFallbackStrategy() {
        // 測試降級策略
        String id = "TEST002";
        
        // 模擬鎖獲取失敗
        when(lockKeyGenerator.isValidStorageLockKey(anyString())).thenReturn(false);
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong())).thenReturn(false);
        when(distributedLock.isLocked(anyString())).thenReturn(true);
        
        // 執行測試方法
        String result = testService.testFallbackStrategy(id);
        
        // 驗證降級邏輯執行
        assertEquals("降級處理：TEST002", result);
        
        // 驗證鎖操作被調用
        verify(distributedLock).tryLock(anyString(), anyLong(), anyLong());
    }
    
    @Test
    void testSpelExpressionParsing() {
        // 測試SpEL表達式解析
        String commodityCode = "PRODUCT003";
        String expectedLockKey = "distributed:lock:storage:product003";
        
        // 模擬成功場景
        when(lockKeyGenerator.isValidStorageLockKey(anyString())).thenReturn(false);
        when(lockKeyGenerator.generateStorageLockKey(commodityCode)).thenReturn(expectedLockKey);
        when(distributedLock.tryLock(eq(expectedLockKey), anyLong(), anyLong())).thenReturn(true);
        when(distributedLock.isLocked(expectedLockKey)).thenReturn(false);
        
        // 執行測試
        String result = testService.testStorageOperation(commodityCode, 15);
        
        // 驗證結果
        assertNotNull(result);
        assertTrue(result.contains("PRODUCT003"));
        
        // 驗證SpEL表達式被正確解析並生成了正確的鎖鍵
        verify(distributedLock).tryLock(expectedLockKey, 5L, 30L);
    }
    
    @Test
    void testCrossServiceLockConflictDetection() {
        // 測試跨服務鎖衝突檢測
        String commodityCode = "PRODUCT004";
        String expectedLockKey = "distributed:lock:storage:product004";
        
        // 模擬跨服務鎖衝突場景
        when(lockKeyGenerator.isValidStorageLockKey(anyString())).thenReturn(false);
        when(lockKeyGenerator.generateStorageLockKey(commodityCode)).thenReturn(expectedLockKey);
        when(distributedLock.isLocked(expectedLockKey)).thenReturn(true); // 鎖被其他服務持有
        when(distributedLock.tryLock(eq(expectedLockKey), anyLong(), anyLong())).thenReturn(true);
        
        // 執行測試
        String result = testService.testStorageOperation(commodityCode, 20);
        
        // 驗證方法執行成功
        assertNotNull(result);
        
        // 驗證衝突檢測被調用
        verify(distributedLock).isLocked(expectedLockKey);
        verify(distributedLock).tryLock(expectedLockKey, 5L, 30L);
    }
}