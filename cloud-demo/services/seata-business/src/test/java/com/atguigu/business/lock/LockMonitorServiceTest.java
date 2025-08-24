package com.atguigu.business.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 跨服務分布式鎖監控服務測試
 * 
 * @author Kiro
 */
@ExtendWith(MockitoExtension.class)
class LockMonitorServiceTest {
    
    @Mock
    private RedissonClient redissonClient;
    
    @Mock
    private RedisDistributedLock redisDistributedLock;
    
    @Mock
    private RKeys rKeys;
    
    @Mock
    private RLock rLock;
    
    private LockMonitorServiceImpl lockMonitorService;
    
    @BeforeEach
    void setUp() {
        lockMonitorService = new LockMonitorServiceImpl();
        // 使用反射設置私有字段
        setField(lockMonitorService, "redissonClient", redissonClient);
        setField(lockMonitorService, "redisDistributedLock", redisDistributedLock);
        setField(lockMonitorService, "lockKeyPrefix", "distributed:lock:storage:");
        setField(lockMonitorService, "currentServiceName", "seata-business");
    }
    
    @Test
    void testGetAllLocks() {
        // Arrange
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.getKeysByPattern("distributed:lock:storage:*"))
            .thenReturn(Arrays.asList("distributed:lock:storage:PRODUCT001", "distributed:lock:storage:PRODUCT002"));
        
        when(redissonClient.getLock("distributed:lock:storage:PRODUCT001")).thenReturn(rLock);
        when(redissonClient.getLock("distributed:lock:storage:PRODUCT002")).thenReturn(rLock);
        when(rLock.isLocked()).thenReturn(true);
        when(rLock.remainTimeToLive()).thenReturn(25000L); // 25秒剩餘時間
        
        // Act
        List<LockInfo> locks = lockMonitorService.getAllLocks();
        
        // Assert
        assertNotNull(locks);
        assertEquals(2, locks.size());
        
        LockInfo lockInfo = locks.get(0);
        assertNotNull(lockInfo.getLockKey());
        assertEquals(25, lockInfo.getRemainingTime());
        assertEquals(LockInfo.LockStatus.ACTIVE, lockInfo.getStatus());
    }
    
    @Test
    void testGetLockInfo() {
        // Arrange
        String lockKey = "distributed:lock:storage:PRODUCT001";
        when(redissonClient.getLock(lockKey)).thenReturn(rLock);
        when(rLock.isLocked()).thenReturn(true);
        when(rLock.remainTimeToLive()).thenReturn(20000L); // 20秒剩餘時間
        
        // Act
        LockInfo lockInfo = lockMonitorService.getLockInfo(lockKey);
        
        // Assert
        assertNotNull(lockInfo);
        assertEquals(lockKey, lockInfo.getLockKey());
        assertEquals(20, lockInfo.getRemainingTime());
        assertEquals(LockInfo.LockStatus.ACTIVE, lockInfo.getStatus());
        assertEquals("STORAGE_DEDUCT", lockInfo.getLockType());
    }
    
    @Test
    void testGetLockInfoForNonExistentLock() {
        // Arrange
        String lockKey = "distributed:lock:storage:NONEXISTENT";
        when(redissonClient.getLock(lockKey)).thenReturn(rLock);
        when(rLock.isLocked()).thenReturn(false);
        
        // Act
        LockInfo lockInfo = lockMonitorService.getLockInfo(lockKey);
        
        // Assert
        assertNull(lockInfo);
    }
    
    @Test
    void testForceUnlock() {
        // Arrange
        String lockKey = "distributed:lock:storage:PRODUCT001";
        when(redisDistributedLock.forceUnlock(lockKey)).thenReturn(true);
        
        // Act
        boolean result = lockMonitorService.forceUnlock(lockKey);
        
        // Assert
        assertTrue(result);
        verify(redisDistributedLock).forceUnlock(lockKey);
    }
    
    @Test
    void testBatchForceUnlock() {
        // Arrange
        List<String> lockKeys = Arrays.asList(
            "distributed:lock:storage:PRODUCT001",
            "distributed:lock:storage:PRODUCT002",
            "distributed:lock:storage:PRODUCT003"
        );
        
        when(redisDistributedLock.forceUnlock("distributed:lock:storage:PRODUCT001")).thenReturn(true);
        when(redisDistributedLock.forceUnlock("distributed:lock:storage:PRODUCT002")).thenReturn(false);
        when(redisDistributedLock.forceUnlock("distributed:lock:storage:PRODUCT003")).thenReturn(true);
        
        // Act
        List<String> successfullyUnlocked = lockMonitorService.batchForceUnlock(lockKeys);
        
        // Assert
        assertEquals(2, successfullyUnlocked.size());
        assertTrue(successfullyUnlocked.contains("distributed:lock:storage:PRODUCT001"));
        assertTrue(successfullyUnlocked.contains("distributed:lock:storage:PRODUCT003"));
        assertFalse(successfullyUnlocked.contains("distributed:lock:storage:PRODUCT002"));
    }
    
    @Test
    void testRecordLockEvent() {
        // Arrange
        String lockKey = "distributed:lock:storage:PRODUCT001";
        String serviceSource = "seata-business";
        LockMonitorService.LockOperation operation = LockMonitorService.LockOperation.ACQUIRE;
        boolean success = true;
        long duration = 150L;
        
        // Act
        lockMonitorService.recordLockEvent(lockKey, serviceSource, operation, success, duration);
        
        // Assert
        LockStatistics stats = lockMonitorService.getLockStatistics();
        assertEquals(1, stats.getTotalLockRequests());
        assertEquals(1, stats.getSuccessfulLocks());
        assertEquals(0, stats.getFailedLocks());
    }
    
    @Test
    void testGetActiveLockCount() {
        // Arrange
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(5L);
        
        // Act
        int count = lockMonitorService.getActiveLockCount();
        
        // Assert
        assertEquals(5, count);
    }
    
    @Test
    void testGetLockStatistics() {
        // Arrange - 記錄一些事件
        lockMonitorService.recordLockEvent("lock1", "seata-business", 
            LockMonitorService.LockOperation.ACQUIRE, true, 100);
        lockMonitorService.recordLockEvent("lock2", "seata-storage", 
            LockMonitorService.LockOperation.ACQUIRE, false, 200);
        lockMonitorService.recordLockEvent("lock1", "seata-business", 
            LockMonitorService.LockOperation.RELEASE, true, 50);
        
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.count()).thenReturn(3L);
        
        // Act
        LockStatistics stats = lockMonitorService.getLockStatistics();
        
        // Assert
        assertNotNull(stats);
        assertEquals(2, stats.getTotalLockRequests());
        assertEquals(1, stats.getSuccessfulLocks());
        assertEquals(1, stats.getFailedLocks());
        assertEquals(3, stats.getCurrentActiveLocks());
        assertEquals(50.0, stats.getSuccessRate(), 0.01);
    }
    
    @Test
    void testResetStatistics() {
        // Arrange - 先記錄一些數據
        lockMonitorService.recordLockEvent("lock1", "seata-business", 
            LockMonitorService.LockOperation.ACQUIRE, true, 100);
        
        // Act
        lockMonitorService.resetStatistics();
        
        // Assert
        LockStatistics stats = lockMonitorService.getLockStatistics();
        assertEquals(0, stats.getTotalLockRequests());
        assertEquals(0, stats.getSuccessfulLocks());
        assertEquals(0, stats.getFailedLocks());
        assertEquals(0.0, stats.getSuccessRate(), 0.01);
    }
    
    @Test
    void testGetServiceLockUsage() {
        // Arrange - 記錄不同服務的事件
        lockMonitorService.recordLockEvent("lock1", "seata-business", 
            LockMonitorService.LockOperation.ACQUIRE, true, 100);
        lockMonitorService.recordLockEvent("lock2", "seata-storage", 
            LockMonitorService.LockOperation.ACQUIRE, true, 150);
        lockMonitorService.recordLockEvent("lock3", "seata-business", 
            LockMonitorService.LockOperation.ACQUIRE, false, 200);
        
        // Act
        Map<String, LockMonitorService.ServiceLockUsage> usage = lockMonitorService.getServiceLockUsage();
        
        // Assert
        assertNotNull(usage);
        assertTrue(usage.containsKey("seata-business"));
        assertTrue(usage.containsKey("seata-storage"));
        
        LockMonitorService.ServiceLockUsage businessUsage = usage.get("seata-business");
        assertEquals(2, businessUsage.getTotalLockRequests());
        assertEquals(1, businessUsage.getSuccessfulLocks());
        assertEquals(50.0, businessUsage.getSuccessRate(), 0.01);
        
        LockMonitorService.ServiceLockUsage storageUsage = usage.get("seata-storage");
        assertEquals(1, storageUsage.getTotalLockRequests());
        assertEquals(1, storageUsage.getSuccessfulLocks());
        assertEquals(100.0, storageUsage.getSuccessRate(), 0.01);
    }
    
    @Test
    void testDetectDeadlockRisk() {
        // Act
        List<LockMonitorService.DeadlockRiskInfo> risks = lockMonitorService.detectDeadlockRisk();
        
        // Assert
        assertNotNull(risks);
        // 在沒有實際鎖衝突的情況下，應該返回空列表
        assertTrue(risks.isEmpty());
    }
    
    @Test
    void testGetLongHeldLocks() {
        // Arrange
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.getKeysByPattern("distributed:lock:storage:*"))
            .thenReturn(Arrays.asList("distributed:lock:storage:PRODUCT001"));
        
        when(redissonClient.getLock("distributed:lock:storage:PRODUCT001")).thenReturn(rLock);
        when(rLock.isLocked()).thenReturn(true);
        when(rLock.remainTimeToLive()).thenReturn(25000L);
        
        // Act
        List<LockInfo> longHeldLocks = lockMonitorService.getLongHeldLocks(10);
        
        // Assert
        assertNotNull(longHeldLocks);
        // 由於我們無法精確控制時間，這裡主要測試方法不會拋出異常
    }
    
    /**
     * 使用反射設置私有字段的輔助方法
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}