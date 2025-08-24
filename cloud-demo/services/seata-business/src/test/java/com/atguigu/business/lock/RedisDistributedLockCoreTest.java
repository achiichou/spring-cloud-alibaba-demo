package com.atguigu.business.lock;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisDistributedLock核心功能測試
 * 專注於測試分布式鎖的核心邏輯，包括跨服務場景
 * 使用Mock Redis客戶端進行測試
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisDistributedLock 核心功能測試")
class RedisDistributedLockCoreTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    private RedisDistributedLock distributedLock;

    private static final String TEST_LOCK_KEY = "distributed:lock:storage:ITEM001";
    private static final String BUSINESS_SERVICE = "seata-business";
    private static final String STORAGE_SERVICE = "seata-storage";

    @BeforeEach
    void setUp() {
        distributedLock = new RedisDistributedLock();
        ReflectionTestUtils.setField(distributedLock, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(distributedLock, "serviceName", BUSINESS_SERVICE);
        ReflectionTestUtils.setField(distributedLock, "defaultWaitTime", 5L);
        ReflectionTestUtils.setField(distributedLock, "defaultLeaseTime", 30L);
        
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @AfterEach
    void tearDown() {
        distributedLock.clearLockContext();
    }

    @Nested
    @DisplayName("跨服務鎖功能測試")
    class CrossServiceLockTests {

        @Test
        @DisplayName("跨服務鎖獲取和釋放 - 驗證服務標識")
        void testCrossServiceLockWithServiceIdentification() throws InterruptedException {
            // Given
            when(rLock.tryLock(5L, 30L, TimeUnit.SECONDS)).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            // When
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 5L, 30L);

            // Then
            assertTrue(acquired, "應該成功獲取跨服務鎖");
            
            // 驗證跨服務上下文
            CrossServiceLockContext context = distributedLock.getLockContext(TEST_LOCK_KEY);
            assertNotNull(context, "應該記錄跨服務鎖上下文");
            assertEquals(BUSINESS_SERVICE, context.getServiceSource(), "服務來源應該正確");
            assertEquals(TEST_LOCK_KEY, context.getLockKey(), "鎖鍵應該正確");
            
            // 驗證鎖持有者標識包含服務信息
            String lockHolder = context.getLockHolder();
            assertTrue(lockHolder.contains(BUSINESS_SERVICE), "鎖持有者標識應該包含服務名");
            assertTrue(lockHolder.contains(Thread.currentThread().getName()), "鎖持有者標識應該包含線程名");

            // 釋放鎖
            distributedLock.unlock(TEST_LOCK_KEY);
            verify(rLock).unlock();
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY), "釋放後上下文應該被清理");
        }

        @Test
        @DisplayName("模擬不同服務的鎖上下文差異")
        void testDifferentServiceLockContexts() throws InterruptedException {
            // Given - 創建兩個不同服務的鎖實例
            RedisDistributedLock businessLock = createServiceLock(BUSINESS_SERVICE);
            RedisDistributedLock storageLock = createServiceLock(STORAGE_SERVICE);
            
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            // When
            boolean businessAcquired = businessLock.tryLock("business:lock", 5L, 30L);
            boolean storageAcquired = storageLock.tryLock("storage:lock", 5L, 30L);

            // Then
            assertTrue(businessAcquired, "business服務應該成功獲取鎖");
            assertTrue(storageAcquired, "storage服務應該成功獲取鎖");

            CrossServiceLockContext businessContext = businessLock.getLockContext("business:lock");
            CrossServiceLockContext storageContext = storageLock.getLockContext("storage:lock");

            assertNotNull(businessContext, "business服務應該有鎖上下文");
            assertNotNull(storageContext, "storage服務應該有鎖上下文");

            assertEquals(BUSINESS_SERVICE, businessContext.getServiceSource());
            assertEquals(STORAGE_SERVICE, storageContext.getServiceSource());

            // 驗證不同服務的鎖持有者標識不同
            assertNotEquals(businessContext.getLockHolder(), storageContext.getLockHolder(),
                          "不同服務的鎖持有者標識應該不同");
        }
    }

    @Nested
    @DisplayName("超時機制測試")
    class TimeoutMechanismTests {

        @Test
        @DisplayName("鎖獲取超時測試 - 模擬跨服務競爭")
        void testLockAcquisitionTimeout() throws InterruptedException {
            // Given - 模擬第一次獲取失敗（其他服務持有鎖）
            when(rLock.tryLock(2L, 30L, TimeUnit.SECONDS)).thenReturn(false);

            // When
            long startTime = System.currentTimeMillis();
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 2L, 30L);
            long endTime = System.currentTimeMillis();

            // Then
            assertFalse(acquired, "應該獲取鎖超時失敗");
            verify(rLock).tryLock(2L, 30L, TimeUnit.SECONDS);
            
            // 驗證沒有記錄上下文
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY), "超時失敗時不應該記錄上下文");
        }

        @Test
        @DisplayName("鎖自動過期測試")
        void testLockAutoExpiration() throws InterruptedException {
            // Given
            when(rLock.tryLock(5L, 2L, TimeUnit.SECONDS)).thenReturn(true);
            when(rLock.remainTimeToLive())
                .thenReturn(2000L)  // 2秒
                .thenReturn(1000L)  // 1秒
                .thenReturn(0L);    // 已過期

            // When
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 5L, 2L);

            // Then
            assertTrue(acquired, "應該成功獲取鎖");
            
            // 檢查剩餘時間遞減
            assertEquals(2000L, distributedLock.getRemainingTime(TEST_LOCK_KEY));
            assertEquals(1000L, distributedLock.getRemainingTime(TEST_LOCK_KEY));
            assertEquals(0L, distributedLock.getRemainingTime(TEST_LOCK_KEY));
        }
    }

    @Nested
    @DisplayName("併發場景測試")
    class ConcurrencyScenarioTests {

        @Test
        @DisplayName("跨服務併發鎖競爭測試")
        void testCrossServiceConcurrentLockCompetition() throws InterruptedException {
            // Given
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger businessServiceCount = new AtomicInteger(0);
            AtomicInteger storageServiceCount = new AtomicInteger(0);

            // 模擬只有前幾次獲取成功
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true, true, false, false, false, false, false, false, false, false);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When - 模擬兩種服務的併發請求
            for (int i = 0; i < threadCount; i++) {
                final boolean isBusinessService = i % 2 == 0;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        String serviceName = isBusinessService ? BUSINESS_SERVICE : STORAGE_SERVICE;
                        RedisDistributedLock lock = createServiceLock(serviceName);
                        
                        boolean acquired = lock.tryLock(TEST_LOCK_KEY, 1L, 5L);
                        if (acquired) {
                            successCount.incrementAndGet();
                            if (isBusinessService) {
                                businessServiceCount.incrementAndGet();
                            } else {
                                storageServiceCount.incrementAndGet();
                            }
                            
                            // 模擬業務處理
                            Thread.sleep(50);
                            lock.unlock(TEST_LOCK_KEY);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);

            // Then
            assertTrue(completed, "所有線程應該完成");
            assertTrue(successCount.get() >= 1, "至少應該有線程成功獲取鎖");
            assertTrue(successCount.get() <= 2, "成功獲取鎖的線程數應該受限");

            executor.shutdown();
        }

        @Test
        @DisplayName("同一服務多線程鎖操作測試")
        void testSameServiceMultiThreadLockOperations() throws InterruptedException {
            // Given
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger operationCount = new AtomicInteger(0);

            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When - 同一服務的多個線程操作不同的鎖
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        String lockKey = TEST_LOCK_KEY + ":" + threadIndex;
                        RedisDistributedLock lock = createServiceLock(BUSINESS_SERVICE);
                        
                        boolean acquired = lock.tryLock(lockKey, 1L, 5L);
                        if (acquired) {
                            operationCount.incrementAndGet();
                            
                            // 驗證上下文
                            CrossServiceLockContext context = lock.getLockContext(lockKey);
                            assertNotNull(context, "應該有鎖上下文");
                            assertEquals(BUSINESS_SERVICE, context.getServiceSource(), "服務來源應該正確");
                            
                            lock.unlock(lockKey);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);

            // Then
            assertTrue(completed, "所有線程應該完成");
            assertEquals(threadCount, operationCount.get(), "所有操作都應該成功");

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("異常處理測試")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Redis連接異常處理")
        void testRedisConnectionException() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis connection failed"));

            // When
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 5L, 30L);

            // Then
            assertFalse(acquired, "連接異常時應該返回false");
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY), "異常時不應該記錄上下文");
        }

        @Test
        @DisplayName("線程中斷異常處理")
        void testInterruptedException() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("Thread interrupted"));

            // When
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 5L, 30L);

            // Then
            assertFalse(acquired, "線程中斷時應該返回false");
            assertTrue(Thread.currentThread().isInterrupted(), "線程中斷狀態應該被恢復");
            
            // 清理中斷狀態
            Thread.interrupted();
        }

        @Test
        @DisplayName("釋放鎖異常處理")
        void testUnlockException() {
            // Given
            when(rLock.isHeldByCurrentThread()).thenReturn(true);
            doThrow(new RuntimeException("Unlock failed")).when(rLock).unlock();

            // 先建立上下文
            try {
                when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
                distributedLock.tryLock(TEST_LOCK_KEY, 5L, 30L);
            } catch (InterruptedException e) {
                fail("Setup should not fail");
            }

            // When
            assertDoesNotThrow(() -> distributedLock.unlock(TEST_LOCK_KEY), "釋放鎖異常不應該拋出");

            // Then - 驗證上下文被清理
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY), "異常時上下文應該被清理");
        }
    }

    @Nested
    @DisplayName("管理功能測試")
    class ManagementFunctionTests {

        @Test
        @DisplayName("強制釋放跨服務鎖")
        void testForceUnlockCrossServiceLock() {
            // Given
            when(rLock.forceUnlock()).thenReturn(true);

            // When
            boolean result = distributedLock.forceUnlock(TEST_LOCK_KEY);

            // Then
            assertTrue(result, "應該成功強制釋放鎖");
            verify(rLock).forceUnlock();
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY), "強制釋放後上下文應該被清理");
        }

        @Test
        @DisplayName("查詢鎖狀態 - 跨服務場景")
        void testLockStatusInCrossServiceScenario() {
            // Given
            when(rLock.isLocked()).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(false); // 模擬其他服務持有
            when(rLock.remainTimeToLive()).thenReturn(25000L);

            // When
            boolean isLocked = distributedLock.isLocked(TEST_LOCK_KEY);
            boolean isHeldByCurrentThread = distributedLock.isHeldByCurrentThread(TEST_LOCK_KEY);
            long remainingTime = distributedLock.getRemainingTime(TEST_LOCK_KEY);

            // Then
            assertTrue(isLocked, "鎖應該存在");
            assertFalse(isHeldByCurrentThread, "當前線程不應該持有鎖（其他服務持有）");
            assertEquals(25000L, remainingTime, "剩餘時間應該正確");
        }

        @Test
        @DisplayName("清理鎖上下文")
        void testClearLockContext() throws InterruptedException {
            // Given - 先獲取一些鎖
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            distributedLock.tryLock(TEST_LOCK_KEY, 5L, 30L);
            distributedLock.tryLock(TEST_LOCK_KEY + ":2", 5L, 30L);

            assertNotNull(distributedLock.getLockContext(TEST_LOCK_KEY), "應該有第一個鎖的上下文");
            assertNotNull(distributedLock.getLockContext(TEST_LOCK_KEY + ":2"), "應該有第二個鎖的上下文");

            // When
            distributedLock.clearLockContext();

            // Then
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY), "第一個鎖的上下文應該被清理");
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY + ":2"), "第二個鎖的上下文應該被清理");
        }

        @Test
        @DisplayName("獲取服務名稱")
        void testGetServiceName() {
            // When
            String serviceName = distributedLock.getServiceName();

            // Then
            assertEquals(BUSINESS_SERVICE, serviceName, "應該返回正確的服務名稱");
        }
    }

    @Nested
    @DisplayName("邊界條件測試")
    class EdgeCaseTests {

        @Test
        @DisplayName("空鎖鍵處理")
        void testEmptyLockKey() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            // When & Then
            assertDoesNotThrow(() -> {
                distributedLock.tryLock("", 5L, 30L);
                distributedLock.tryLock(null, 5L, 30L);
                distributedLock.unlock("");
                distributedLock.unlock(null);
                distributedLock.isLocked("");
                distributedLock.isLocked(null);
            }, "空鎖鍵不應該拋出異常");
        }

        @Test
        @DisplayName("極端時間參數處理")
        void testExtremeTimeParameters() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            // When & Then
            assertDoesNotThrow(() -> {
                distributedLock.tryLock(TEST_LOCK_KEY, 0L, 30L);
                distributedLock.tryLock(TEST_LOCK_KEY, 5L, 0L);
                distributedLock.tryLock(TEST_LOCK_KEY, -1L, -1L);
                distributedLock.tryLock(TEST_LOCK_KEY, Long.MAX_VALUE, Long.MAX_VALUE);
            }, "極端時間參數不應該拋出異常");
        }

        @Test
        @DisplayName("重複操作處理")
        void testRepeatedOperations() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            // When - 重複獲取和釋放同一個鎖
            boolean first = distributedLock.tryLock(TEST_LOCK_KEY, 5L, 30L);
            boolean second = distributedLock.tryLock(TEST_LOCK_KEY, 5L, 30L);

            // Then
            assertTrue(first, "第一次獲取應該成功");
            assertTrue(second, "第二次獲取也應該成功（重入）");

            // 重複釋放
            assertDoesNotThrow(() -> {
                distributedLock.unlock(TEST_LOCK_KEY);
                distributedLock.unlock(TEST_LOCK_KEY); // 重複釋放
            }, "重複釋放不應該拋出異常");
        }
    }

    // 輔助方法
    private RedisDistributedLock createServiceLock(String serviceName) {
        RedisDistributedLock lock = new RedisDistributedLock();
        ReflectionTestUtils.setField(lock, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(lock, "serviceName", serviceName);
        ReflectionTestUtils.setField(lock, "defaultWaitTime", 5L);
        ReflectionTestUtils.setField(lock, "defaultLeaseTime", 30L);
        return lock;
    }
}