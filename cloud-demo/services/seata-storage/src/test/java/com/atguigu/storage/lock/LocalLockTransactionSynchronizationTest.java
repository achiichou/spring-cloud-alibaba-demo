package com.atguigu.storage.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * LocalLockTransactionSynchronization 單元測試
 * 
 * 測試本地事務與分布式鎖同步器的核心功能：
 * 1. 鎖註冊到事務
 * 2. 事務提交後釋放鎖
 * 3. 事務回滾後立即釋放鎖
 * 4. 事務生命週期同步
 */
@ExtendWith(MockitoExtension.class)
class LocalLockTransactionSynchronizationTest {
    
    @Mock
    private DistributedLock distributedLock;
    
    @Mock
    private CrossServiceLockMetricsCollector metricsCollector;
    
    private LocalLockTransactionSynchronization synchronization;
    
    @BeforeEach
    void setUp() {
        synchronization = new LocalLockTransactionSynchronization();
        ReflectionTestUtils.setField(synchronization, "distributedLock", distributedLock);
        ReflectionTestUtils.setField(synchronization, "metricsCollector", metricsCollector);
        ReflectionTestUtils.setField(synchronization, "serviceName", "seata-storage");
        ReflectionTestUtils.setField(synchronization, "autoReleaseOnTransactionEnd", true);
        ReflectionTestUtils.setField(synchronization, "releaseTimeoutMs", 5000L);
        
        // 初始化
        synchronization.init();
    }
    
    @Test
    void testRegisterLockToTransaction() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        
        // 模擬事務同步管理器處於活躍狀態
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName("test-transaction");
        
        try {
            // When
            synchronization.registerLockToTransaction(lockKey, businessContext);
            
            // Then
            assertTrue(synchronization.isLockRegisteredToTransaction(lockKey));
            
            ConcurrentMap<String, LocalLockTransactionSynchronization.LockTransactionContext> locks = 
                synchronization.getCurrentTransactionLocks();
            assertEquals(1, locks.size());
            assertTrue(locks.containsKey(lockKey));
            
            LocalLockTransactionSynchronization.LockTransactionContext context = locks.get(lockKey);
            assertEquals(lockKey, context.getLockKey());
            assertEquals("test-transaction", context.getTransactionName());
            assertEquals(businessContext, context.getBusinessContext());
            assertFalse(context.isReleased());
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testRegisterLockToTransactionWhenNoActiveTransaction() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        
        // When - 沒有活躍事務
        synchronization.registerLockToTransaction(lockKey, businessContext);
        
        // Then - 鎖不應該被註冊
        assertFalse(synchronization.isLockRegisteredToTransaction(lockKey));
        assertTrue(synchronization.getCurrentTransactionLocks().isEmpty());
    }
    
    @Test
    void testUnregisterLockFromTransaction() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName("test-transaction");
        
        try {
            synchronization.registerLockToTransaction(lockKey, businessContext);
            assertTrue(synchronization.isLockRegisteredToTransaction(lockKey));
            
            // When
            synchronization.unregisterLockFromTransaction(lockKey);
            
            // Then
            assertFalse(synchronization.isLockRegisteredToTransaction(lockKey));
            assertTrue(synchronization.getCurrentTransactionLocks().isEmpty());
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testAfterCommit() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName("test-transaction");
        
        try {
            synchronization.registerLockToTransaction(lockKey, businessContext);
            when(distributedLock.isHeldByCurrentThread(lockKey)).thenReturn(true);
            
            // When
            synchronization.afterCommit();
            
            // Then
            verify(distributedLock).unlock(lockKey);
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testAfterCompletionWithRollback() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName("test-transaction");
        
        try {
            synchronization.registerLockToTransaction(lockKey, businessContext);
            when(distributedLock.isHeldByCurrentThread(lockKey)).thenReturn(true);
            
            // When - 事務回滾
            synchronization.afterCompletion(LocalLockTransactionSynchronization.STATUS_ROLLED_BACK);
            
            // Then
            verify(distributedLock).unlock(lockKey);
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testAfterCompletionWithCommit() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName("test-transaction");
        
        try {
            synchronization.registerLockToTransaction(lockKey, businessContext);
            
            // 模擬鎖已經在afterCommit中釋放
            ConcurrentMap<String, LocalLockTransactionSynchronization.LockTransactionContext> locks = 
                synchronization.getCurrentTransactionLocks();
            locks.get(lockKey).setReleased(true);
            
            // When - 事務提交
            synchronization.afterCompletion(LocalLockTransactionSynchronization.STATUS_COMMITTED);
            
            // Then - 不應該再次釋放鎖
            verify(distributedLock, never()).unlock(lockKey);
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testFlushWithLockNotHeldByCurrentThread() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName("test-transaction");
        
        try {
            synchronization.registerLockToTransaction(lockKey, businessContext);
            when(distributedLock.isHeldByCurrentThread(lockKey)).thenReturn(false);
            
            // When
            synchronization.flush();
            
            // Then - 應該記錄鎖丟失事件
            verify(metricsCollector).recordLockLost(lockKey, "seata-storage", "test-transaction");
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testBeforeCommitRecordsMetrics() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName("test-transaction");
        
        try {
            synchronization.registerLockToTransaction(lockKey, businessContext);
            
            // 等待一小段時間以確保持有時間 > 0
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // When
            synchronization.beforeCommit(false);
            
            // Then - 應該記錄事務鎖持有時間
            verify(metricsCollector).recordTransactionLockHoldTime(
                eq(lockKey), 
                eq("seata-storage"), 
                eq("test-transaction"), 
                any(java.time.Duration.class)
            );
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testForceReleaseTransactionLocks() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        String transactionName = "test-transaction";
        
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName(transactionName);
        
        try {
            synchronization.registerLockToTransaction(lockKey, businessContext);
            
            // 模擬RedisDistributedLock
            RedisDistributedLock redisLock = mock(RedisDistributedLock.class);
            when(redisLock.forceUnlock(lockKey)).thenReturn(true);
            ReflectionTestUtils.setField(synchronization, "distributedLock", redisLock);
            
            // When
            int releasedCount = synchronization.forceReleaseTransactionLocks(transactionName);
            
            // Then
            assertEquals(1, releasedCount);
            verify(redisLock).forceUnlock(lockKey);
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testGetStatistics() {
        // Given
        String lockKey = "distributed:lock:storage:test-commodity";
        String businessContext = "test-deduct";
        String transactionName = "test-transaction";
        
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName(transactionName);
        
        try {
            synchronization.registerLockToTransaction(lockKey, businessContext);
            
            // When
            LocalLockTransactionSynchronization.TransactionLockStatistics stats = 
                synchronization.getStatistics();
            
            // Then
            assertNotNull(stats);
            assertEquals(1, stats.getActiveLockCount());
            assertEquals(transactionName, stats.getCurrentTransactionName());
            assertEquals("seata-storage", stats.getServiceName());
            assertTrue(stats.getMaxHoldTime() >= 0);
            assertTrue(stats.getAverageHoldTime() >= 0);
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testIsInLocalTransaction() {
        // When - 沒有活躍事務
        assertFalse(synchronization.isInLocalTransaction());
        
        // Given - 啟動事務同步
        TransactionSynchronizationManager.initSynchronization();
        
        try {
            // When - 有活躍事務
            assertTrue(synchronization.isInLocalTransaction());
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
    
    @Test
    void testGetCurrentLocalTransactionName() {
        // Given
        String transactionName = "test-transaction";
        
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionName(transactionName);
        
        try {
            // When
            String currentName = synchronization.getCurrentLocalTransactionName();
            
            // Then
            assertEquals(transactionName, currentName);
            
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}