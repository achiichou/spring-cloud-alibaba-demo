package com.atguigu.business.lock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分布式鎖集成驗證測試
 * 驗證分布式鎖相關組件是否正確配置和集成
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "seata.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.config.enabled=false"
})
public class DistributedLockIntegrationVerificationTest {

    @Test
    public void testDistributedLockAnnotationExists() {
        // 驗證@DistributedLockable註解存在
        assertNotNull(DistributedLockable.class);
        
        // 驗證註解的屬性方法存在
        try {
            DistributedLockable.class.getMethod("key");
            DistributedLockable.class.getMethod("waitTime");
            DistributedLockable.class.getMethod("leaseTime");
            DistributedLockable.class.getMethod("failStrategy");
            DistributedLockable.class.getMethod("businessContext");
        } catch (NoSuchMethodException e) {
            fail("DistributedLockable annotation methods not found: " + e.getMessage());
        }
    }

    @Test
    public void testDistributedLockExceptionExists() {
        // 驗證DistributedLockException類存在
        assertNotNull(DistributedLockException.class);
        
        // 測試異常創建
        DistributedLockException exception = new DistributedLockException(
            LockErrorCode.LOCK_ACQUIRE_TIMEOUT, 
            "test-key", 
            "test-service"
        );
        
        assertNotNull(exception);
        assertEquals(LockErrorCode.LOCK_ACQUIRE_TIMEOUT, exception.getErrorCode());
        assertEquals("test-key", exception.getLockKey());
        assertEquals("test-service", exception.getServiceSource());
    }

    @Test
    public void testLockErrorCodeExists() {
        // 驗證LockErrorCode枚舉存在
        assertNotNull(LockErrorCode.class);
        
        // 驗證主要錯誤碼
        assertNotNull(LockErrorCode.LOCK_ACQUIRE_TIMEOUT);
        assertNotNull(LockErrorCode.LOCK_RELEASE_FAILED);
        assertNotNull(LockErrorCode.REDIS_CONNECTION_ERROR);
        assertNotNull(LockErrorCode.INVALID_LOCK_KEY);
        assertNotNull(LockErrorCode.LOCK_NOT_HELD);
    }

    @Test
    public void testLockFailStrategyExists() {
        // 驗證LockFailStrategy枚舉存在
        assertNotNull(LockFailStrategy.class);
        
        // 驗證主要策略
        assertNotNull(LockFailStrategy.EXCEPTION);
        assertNotNull(LockFailStrategy.RETURN_NULL);
        assertNotNull(LockFailStrategy.RETRY);
        assertNotNull(LockFailStrategy.FALLBACK);
        assertNotNull(LockFailStrategy.IGNORE);
        
        // 驗證策略方法
        assertTrue(LockFailStrategy.EXCEPTION.shouldThrowException());
        assertTrue(LockFailStrategy.RETRY.shouldRetry());
        assertTrue(LockFailStrategy.FALLBACK.shouldFallback());
        assertTrue(LockFailStrategy.IGNORE.shouldIgnore());
    }

    @Test
    public void testCrossServiceLockKeyGeneratorExists() {
        // 驗證CrossServiceLockKeyGenerator類存在
        assertNotNull(CrossServiceLockKeyGenerator.class);
        
        // 創建實例並測試基本功能
        CrossServiceLockKeyGenerator generator = new CrossServiceLockKeyGenerator();
        
        String lockKey = generator.generateStorageLockKey("TEST001");
        assertNotNull(lockKey);
        assertTrue(lockKey.contains("test001")); // 商品編碼會被轉換為小寫
        assertTrue(lockKey.startsWith("distributed:lock:storage:"));
    }

    @Test
    public void testDistributedLockInterfaceExists() {
        // 驗證DistributedLock接口存在
        assertNotNull(DistributedLock.class);
        
        // 驗證接口方法存在
        try {
            DistributedLock.class.getMethod("tryLock", String.class, long.class, long.class);
            DistributedLock.class.getMethod("unlock", String.class);
            DistributedLock.class.getMethod("isLocked", String.class);
        } catch (NoSuchMethodException e) {
            fail("DistributedLock interface methods not found: " + e.getMessage());
        }
    }

    @Test
    public void testDistributedLockAspectExists() {
        // 驗證DistributedLockAspect類存在
        assertNotNull(DistributedLockAspect.class);
    }

    @Test
    public void testRedisDistributedLockExists() {
        // 驗證RedisDistributedLock類存在
        assertNotNull(RedisDistributedLock.class);
    }


}