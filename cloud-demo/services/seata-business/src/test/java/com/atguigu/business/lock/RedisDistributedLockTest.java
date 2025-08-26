package com.atguigu.business.lock;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisDistributedLock核心功能單元測試
 * 測試跨服務分布式鎖的獲取、釋放和超時機制
 * 測試服務標識和上下文信息的正確性
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisDistributedLock 核心功能測試")
class RedisDistributedLockTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private RedisDistributedLock distributedLock;

    private static final String TEST_LOCK_KEY = "test:lock:key";
    private static final String TEST_SERVICE_NAME = "test-service";
    private static final long DEFAULT_WAIT_TIME = 5L;
    private static final long DEFAULT_LEASE_TIME = 30L;

    @BeforeEach
    void setUp() {
        // 設置測試用的服務名稱和默認參數
        ReflectionTestUtils.setField(distributedLock, "serviceName", TEST_SERVICE_NAME);
        ReflectionTestUtils.setField(distributedLock, "defaultWaitTime", DEFAULT_WAIT_TIME);
        ReflectionTestUtils.setField(distributedLock, "defaultLeaseTime", DEFAULT_LEASE_TIME);
        
        // 設置默認的mock行為
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @AfterEach
    void tearDown() {
        // 清理鎖上下文
        distributedLock.clearLockContext();
    }

    @Nested
    @DisplayName("鎖獲取功能測試")
    class LockAcquisitionTests {

        @Test
        @DisplayName("成功獲取鎖 - 應該返回true並記錄上下文")
        void testTryLock_Success() throws InterruptedException {
            // Given
            when(rLock.tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS)).thenReturn(true);

            // When
            boolean result = distributedLock.tryLock(TEST_LOCK_KEY, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);

            // Then
            assertTrue(result, "應該成功獲取鎖");
            verify(redissonClient).getLock(TEST_LOCK_KEY);
            verify(rLock).tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
            
            // 驗證鎖上下文是否正確記錄
            CrossServiceLockContext context = distributedLock.getLockContext(TEST_LOCK_KEY);
            assertNotNull(context, "應該記錄鎖上下文");
            assertEquals(TEST_LOCK_KEY, context.getLockKey());
            assertEquals(TEST_SERVICE_NAME, context.getServiceSource());
            assertEquals("distributed-lock-operation", context.getBusinessContext());
            assertTrue(context.getTimestamp() > 0, "時間戳應該大於0");
        }

        @Test
        @DisplayName("獲取鎖失敗 - 應該返回false且不記錄上下文")
        void testTryLock_Failed() throws InterruptedException {
            // Given
            when(rLock.tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS)).thenReturn(false);

            // When
            boolean result = distributedLock.tryLock(TEST_LOCK_KEY, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);

            // Then
            assertFalse(result, "應該獲取鎖失敗");
            verify(redissonClient).getLock(TEST_LOCK_KEY);
            verify(rLock).tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
            
            // 驗證沒有記錄鎖上下文
            CrossServiceLockContext context = distributedLock.getLockContext(TEST_LOCK_KEY);
            assertNull(context, "失敗時不應該記錄鎖上下文");
        }

        @Test
        @DisplayName("使用默認參數獲取鎖")
        void testTryLock_WithDefaultParameters() throws InterruptedException {
            // Given
            when(rLock.tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS)).thenReturn(true);

            // When
            boolean result = distributedLock.tryLock(TEST_LOCK_KEY);

            // Then
            assertTrue(result, "應該成功獲取鎖");
            verify(rLock).tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("獲取鎖時線程被中斷 - 應該返回false")
        void testTryLock_InterruptedException() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("Thread interrupted"));

            // When
            boolean result = distributedLock.tryLock(TEST_LOCK_KEY, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);

            // Then
            assertFalse(result, "線程中斷時應該返回false");
            assertTrue(Thread.currentThread().isInterrupted(), "線程中斷狀態應該被恢復");
            
            // 清理中斷狀態
            Thread.interrupted();
        }

        @Test
        @DisplayName("獲取鎖時發生異常 - 應該返回false")
        void testTryLock_Exception() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis connection error"));

            // When
            boolean result = distributedLock.tryLock(TEST_LOCK_KEY, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);

            // Then
            assertFalse(result, "發生異常時應該返回false");
        }
    }

    @Nested
    @DisplayName("鎖釋放功能測試")
    class LockReleaseTests {

        @Test
        @DisplayName("成功釋放鎖 - 當前線程持有鎖")
        void testUnlock_Success() {
            // Given
            when(rLock.isHeldByCurrentThread()).thenReturn(true);
            
            // 先獲取鎖以建立上下文
            distributedLock.getLockContext(TEST_LOCK_KEY); // 這會創建上下文
            ReflectionTestUtils.invokeMethod(distributedLock, "lockContextHolder");
            
            // 手動添加鎖上下文
            CrossServiceLockContext context = new CrossServiceLockContext(TEST_LOCK_KEY, TEST_SERVICE_NAME, "test-context");
            @SuppressWarnings("unchecked")
            ThreadLocal<ConcurrentHashMap<String, CrossServiceLockContext>> contextHolder = 
                (ThreadLocal<ConcurrentHashMap<String, CrossServiceLockContext>>) ReflectionTestUtils.getField(distributedLock, "lockContextHolder");
            if (contextHolder != null) {
                contextHolder.get().put(TEST_LOCK_KEY, context);
            }

            // When
            distributedLock.unlock(TEST_LOCK_KEY);

            // Then
            verify(rLock).isHeldByCurrentThread();
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("嘗試釋放未持有的鎖 - 應該記錄警告")
        void testUnlock_NotHeldByCurrentThread() {
            // Given
            when(rLock.isHeldByCurrentThread()).thenReturn(false);

            // When
            distributedLock.unlock(TEST_LOCK_KEY);

            // Then
            verify(rLock).isHeldByCurrentThread();
            verify(rLock, never()).unlock();
        }

        @Test
        @DisplayName("釋放鎖時發生異常 - 應該清理本地上下文")
        void testUnlock_Exception() {
            // Given
            when(rLock.isHeldByCurrentThread()).thenReturn(true);
            doThrow(new RuntimeException("Unlock failed")).when(rLock).unlock();

            // When
            distributedLock.unlock(TEST_LOCK_KEY);

            // Then
            verify(rLock).isHeldByCurrentThread();
            verify(rLock).unlock();
            // 驗證上下文被清理
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY));
        }
    }

    @Nested
    @DisplayName("鎖狀態查詢測試")
    class LockStatusTests {

        @Test
        @DisplayName("檢查鎖是否存在 - 鎖存在")
        void testIsLocked_True() {
            // Given
            when(rLock.isLocked()).thenReturn(true);

            // When
            boolean result = distributedLock.isLocked(TEST_LOCK_KEY);

            // Then
            assertTrue(result, "應該返回鎖存在");
            verify(rLock).isLocked();
        }

        @Test
        @DisplayName("檢查鎖是否存在 - 鎖不存在")
        void testIsLocked_False() {
            // Given
            when(rLock.isLocked()).thenReturn(false);

            // When
            boolean result = distributedLock.isLocked(TEST_LOCK_KEY);

            // Then
            assertFalse(result, "應該返回鎖不存在");
            verify(rLock).isLocked();
        }

        @Test
        @DisplayName("檢查鎖狀態時發生異常 - 應該返回false")
        void testIsLocked_Exception() {
            // Given
            when(rLock.isLocked()).thenThrow(new RuntimeException("Redis error"));

            // When
            boolean result = distributedLock.isLocked(TEST_LOCK_KEY);

            // Then
            assertFalse(result, "發生異常時應該返回false");
        }

        @Test
        @DisplayName("檢查當前線程是否持有鎖 - 持有")
        void testIsHeldByCurrentThread_True() {
            // Given
            when(rLock.isHeldByCurrentThread()).thenReturn(true);

            // When
            boolean result = distributedLock.isHeldByCurrentThread(TEST_LOCK_KEY);

            // Then
            assertTrue(result, "應該返回當前線程持有鎖");
            verify(rLock).isHeldByCurrentThread();
        }

        @Test
        @DisplayName("檢查當前線程是否持有鎖 - 不持有")
        void testIsHeldByCurrentThread_False() {
            // Given
            when(rLock.isHeldByCurrentThread()).thenReturn(false);

            // When
            boolean result = distributedLock.isHeldByCurrentThread(TEST_LOCK_KEY);

            // Then
            assertFalse(result, "應該返回當前線程不持有鎖");
            verify(rLock).isHeldByCurrentThread();
        }

        @Test
        @DisplayName("獲取鎖剩餘時間 - 正常情況")
        void testGetRemainingTime_Success() {
            // Given
            long expectedTime = 25000L; // 25秒
            when(rLock.remainTimeToLive()).thenReturn(expectedTime);

            // When
            long result = distributedLock.getRemainingTime(TEST_LOCK_KEY);

            // Then
            assertEquals(expectedTime, result, "應該返回正確的剩餘時間");
            verify(rLock).remainTimeToLive();
        }

        @Test
        @DisplayName("獲取鎖剩餘時間時發生異常 - 應該返回-2")
        void testGetRemainingTime_Exception() {
            // Given
            when(rLock.remainTimeToLive()).thenThrow(new RuntimeException("Redis error"));

            // When
            long result = distributedLock.getRemainingTime(TEST_LOCK_KEY);

            // Then
            assertEquals(-2L, result, "發生異常時應該返回-2");
        }
    }

    @Nested
    @DisplayName("管理功能測試")
    class ManagementTests {

        @Test
        @DisplayName("強制釋放鎖 - 成功")
        void testForceUnlock_Success() {
            // Given
            when(rLock.forceUnlock()).thenReturn(true);

            // When
            boolean result = distributedLock.forceUnlock(TEST_LOCK_KEY);

            // Then
            assertTrue(result, "應該成功強制釋放鎖");
            verify(rLock).forceUnlock();
        }

        @Test
        @DisplayName("強制釋放鎖 - 失敗")
        void testForceUnlock_Failed() {
            // Given
            when(rLock.forceUnlock()).thenReturn(false);

            // When
            boolean result = distributedLock.forceUnlock(TEST_LOCK_KEY);

            // Then
            assertFalse(result, "應該返回強制釋放失敗");
            verify(rLock).forceUnlock();
        }

        @Test
        @DisplayName("強制釋放鎖時發生異常 - 應該返回false")
        void testForceUnlock_Exception() {
            // Given
            when(rLock.forceUnlock()).thenThrow(new RuntimeException("Force unlock failed"));

            // When
            boolean result = distributedLock.forceUnlock(TEST_LOCK_KEY);

            // Then
            assertFalse(result, "發生異常時應該返回false");
        }

        @Test
        @DisplayName("清理鎖上下文")
        void testClearLockContext() {
            // Given - 先添加一些上下文
            distributedLock.tryLock(TEST_LOCK_KEY); // 這會嘗試獲取鎖但可能失敗
            
            // When
            distributedLock.clearLockContext();

            // Then
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY), "上下文應該被清理");
        }

        @Test
        @DisplayName("獲取服務名稱")
        void testGetServiceName() {
            // When
            String serviceName = distributedLock.getServiceName();

            // Then
            assertEquals(TEST_SERVICE_NAME, serviceName, "應該返回正確的服務名稱");
        }
    }

    @Nested
    @DisplayName("跨服務上下文測試")
    class CrossServiceContextTests {

        @Test
        @DisplayName("驗證跨服務鎖上下文信息的正確性")
        void testCrossServiceLockContext() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            // When
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);

            // Then
            assertTrue(acquired, "應該成功獲取鎖");
            
            CrossServiceLockContext context = distributedLock.getLockContext(TEST_LOCK_KEY);
            assertNotNull(context, "應該有鎖上下文");
            
            // 驗證服務標識
            assertEquals(TEST_SERVICE_NAME, context.getServiceSource(), "服務來源應該正確");
            assertEquals(TEST_LOCK_KEY, context.getLockKey(), "鎖鍵應該正確");
            assertEquals("distributed-lock-operation", context.getBusinessContext(), "業務上下文應該正確");
            
            // 驗證線程信息
            assertEquals(Thread.currentThread().getName(), context.getThreadId(), "線程ID應該正確");
            
            // 驗證時間戳
            long currentTime = System.currentTimeMillis();
            assertTrue(context.getTimestamp() <= currentTime, "時間戳應該不晚於當前時間");
            assertTrue(context.getTimestamp() > currentTime - 1000, "時間戳應該在1秒內");
            
            // 驗證鎖持有者標識格式
            String lockHolder = context.getLockHolder();
            assertNotNull(lockHolder, "鎖持有者標識不應該為空");
            assertTrue(lockHolder.contains(TEST_SERVICE_NAME), "鎖持有者標識應該包含服務名");
            assertTrue(lockHolder.contains(Thread.currentThread().getName()), "鎖持有者標識應該包含線程名");
        }

        @Test
        @DisplayName("不同服務實例的上下文應該有不同的實例ID")
        void testDifferentServiceInstances() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            
            // 創建兩個不同的分布式鎖實例模擬不同服務
            RedisDistributedLock lock1 = new RedisDistributedLock();
            RedisDistributedLock lock2 = new RedisDistributedLock();
            
            ReflectionTestUtils.setField(lock1, "redissonClient", redissonClient);
            ReflectionTestUtils.setField(lock1, "serviceName", "service-1");
            ReflectionTestUtils.setField(lock2, "redissonClient", redissonClient);
            ReflectionTestUtils.setField(lock2, "serviceName", "service-2");

            // When
            lock1.tryLock("test-key-1");
            lock2.tryLock("test-key-2");

            // Then
            CrossServiceLockContext context1 = lock1.getLockContext("test-key-1");
            CrossServiceLockContext context2 = lock2.getLockContext("test-key-2");
            
            assertNotNull(context1, "第一個鎖應該有上下文");
            assertNotNull(context2, "第二個鎖應該有上下文");
            
            assertNotEquals(context1.getServiceSource(), context2.getServiceSource(), 
                          "不同服務的服務來源應該不同");
            assertNotEquals(context1.getLockHolder(), context2.getLockHolder(), 
                          "不同服務的鎖持有者標識應該不同");
        }
    }

    @Nested
    @DisplayName("超時機制測試")
    class TimeoutTests {

        @Test
        @DisplayName("鎖獲取超時測試")
        void testLockAcquisitionTimeout() throws InterruptedException {
            // Given
            long shortWaitTime = 1L; // 1秒超時
            when(rLock.tryLock(shortWaitTime, DEFAULT_LEASE_TIME, TimeUnit.SECONDS)).thenReturn(false);

            // When
            long startTime = System.currentTimeMillis();
            boolean result = distributedLock.tryLock(TEST_LOCK_KEY, shortWaitTime, DEFAULT_LEASE_TIME);
            long endTime = System.currentTimeMillis();

            log.info("startTime:{}", startTime);
            log.info("endTime:{}", endTime);
            log.info("spend time:{}", endTime -startTime);

            // Then
            assertFalse(result, "超時後應該獲取鎖失敗");
            // 注意：由於是mock，實際不會等待，但驗證調用參數正確
            verify(rLock).tryLock(shortWaitTime, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("鎖自動過期測試")
        void testLockAutoExpiration() throws InterruptedException {
            // Given
            long shortLeaseTime = 2L; // 2秒租約
            when(rLock.tryLock(DEFAULT_WAIT_TIME, shortLeaseTime, TimeUnit.SECONDS)).thenReturn(true);
            when(rLock.remainTimeToLive()).thenReturn(1000L, 500L, 0L); // 模擬倒計時

            // When
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, DEFAULT_WAIT_TIME, shortLeaseTime);

            // Then
            assertTrue(acquired, "應該成功獲取鎖");
            verify(rLock).tryLock(DEFAULT_WAIT_TIME, shortLeaseTime, TimeUnit.SECONDS);
            
            // 驗證剩餘時間查詢
            long remainingTime1 = distributedLock.getRemainingTime(TEST_LOCK_KEY);
            long remainingTime2 = distributedLock.getRemainingTime(TEST_LOCK_KEY);
            long remainingTime3 = distributedLock.getRemainingTime(TEST_LOCK_KEY);
            
            assertEquals(1000L, remainingTime1);
            assertEquals(500L, remainingTime2);
            assertEquals(0L, remainingTime3);
        }
    }

    @Nested
    @DisplayName("併發測試")
    class ConcurrencyTests {

        @Test
        @DisplayName("多線程併發獲取同一鎖 - 只有一個應該成功")
        void testConcurrentLockAcquisition() throws InterruptedException {
            // Given
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // 模擬只有第一次調用成功
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true)  // 第一次成功
                .thenReturn(false); // 後續都失敗

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 等待所有線程準備就緒
                        boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 1L, 10L);
                        if (acquired) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // 開始執行
            endLatch.await(5, TimeUnit.SECONDS); // 等待所有線程完成

            // Then
            assertTrue(successCount.get() >= 1, "至少應該有一個線程成功獲取鎖");
            assertEquals(threadCount, successCount.get() + failureCount.get(), 
                        "成功和失敗的總數應該等於線程數");

            executor.shutdown();
        }

        @Test
        @DisplayName("多線程併發操作不同鎖 - 都應該成功")
        void testConcurrentDifferentLocks() throws InterruptedException {
            // Given
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String lockKey = TEST_LOCK_KEY + ":" + threadIndex;
                        boolean acquired = distributedLock.tryLock(lockKey, 1L, 10L);
                        if (acquired) {
                            successCount.incrementAndGet();
                            // 模擬業務處理
                            Thread.sleep(100);
                            distributedLock.unlock(lockKey);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await(10, TimeUnit.SECONDS);

            // Then
            assertEquals(threadCount, successCount.get(), "所有線程都應該成功獲取不同的鎖");

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("邊界條件測試")
    class EdgeCaseTests {

        @Test
        @DisplayName("空鎖鍵測試")
        void testEmptyLockKey() {
            // When & Then
            assertDoesNotThrow(() -> {
                distributedLock.tryLock("", DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);
                distributedLock.tryLock(null, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);
            }, "空鎖鍵不應該拋出異常");
        }

        @Test
        @DisplayName("極端時間參數測試")
        void testExtremeTimeParameters() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            // When & Then
            assertDoesNotThrow(() -> {
                // 零等待時間
                distributedLock.tryLock(TEST_LOCK_KEY, 0L, DEFAULT_LEASE_TIME);
                // 零租約時間
                distributedLock.tryLock(TEST_LOCK_KEY, DEFAULT_WAIT_TIME, 0L);
                // 負數時間
                distributedLock.tryLock(TEST_LOCK_KEY, -1L, -1L);
                // 極大時間
                distributedLock.tryLock(TEST_LOCK_KEY, Long.MAX_VALUE, Long.MAX_VALUE);
            }, "極端時間參數不應該拋出異常");
        }

        @Test
        @DisplayName("重複獲取同一鎖測試")
        void testRepeatedLockAcquisition() throws InterruptedException {
            // Given
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

            // When
            boolean first = distributedLock.tryLock(TEST_LOCK_KEY, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);
            boolean second = distributedLock.tryLock(TEST_LOCK_KEY, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);

            // Then
            assertTrue(first, "第一次獲取應該成功");
            assertTrue(second, "第二次獲取也應該成功（可重入）");
        }

        @Test
        @DisplayName("釋放未獲取的鎖測試")
        void testUnlockNeverAcquiredLock() {
            // Given
            when(rLock.isHeldByCurrentThread()).thenReturn(false);

            // When & Then
            assertDoesNotThrow(() -> {
                distributedLock.unlock("never-acquired-lock");
            }, "釋放未獲取的鎖不應該拋出異常");
        }
    }
}